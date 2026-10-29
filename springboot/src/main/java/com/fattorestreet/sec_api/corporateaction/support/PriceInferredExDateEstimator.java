package com.fattorestreet.sec_api.corporateaction.support;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fattorestreet.sec_api.corporateaction.CorporateActionFilingDateService;
import com.fattorestreet.sec_api.corporateaction.EquityCorporateActionService;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.DailyPrice;
import com.fattorestreet.sec_api.repository.DailyPriceRepository;

/**
 * Re-dates synthetic dividend ex-dates for issuers whose record dates never appear on EDGAR
 * (e.g. dividends announced only by press release), using the raw IEX price series.
 *
 * <p>Fits one record-date offset (calendar days after the fiscal period end) per ticker: for each
 * candidate offset, every event's ex-date is derived through the settlement-era rule, and the raw
 * close-to-close return on that date is compared with the drop the dividend predicts,
 * {@code ln(1 - D/prevClose)}. The gain {@code r^2 - (r - expected)^2}, summed over events, peaks at
 * the true offset; a single event is noise, but a regular payer's pattern across many events is not.
 * Fitting the record offset rather than the ex-date offset lets the T+2 to T+1 switch fall out of
 * {@link CorporateActionFilingDateService#computeExDividendDate}.
 *
 * <p>Close-to-close rather than overnight (open vs prior close): an IEX HIST "open" is the first
 * IEX print, not the opening auction, and on AT&amp;T it halved the fit (0.43 vs 0.89 at the true offset).
 *
 * <p>The offset range stays under one quarter wide: a wider range would let the neighbouring
 * quarter's dividend (similar amount, one cadence away) fit almost as well as the right one.
 * Uses only stored IEX prices and SEC amounts, so the result is licensing-clean to persist.
 */
@Component
public class PriceInferredExDateEstimator {

    private static final Logger log = LoggerFactory.getLogger(PriceInferredExDateEstimator.class);
    static final int MIN_RECORD_OFFSET_DAYS = 0;
    static final int MAX_RECORD_OFFSET_DAYS = 85;
    /** Events with a price observation at the fitted offset needed before the fit is trusted. */
    static final int MIN_OBSERVED_EVENTS = 8;
    /** Fitted gain must explain at least this share of the predicted drops' energy (perfect fit = 1, random ≈ -1). */
    static final double MIN_FIT_RATIO = 0.5;
    /** Daily moves beyond this (splits, data errors) are clipped so one row cannot dominate the fit. */
    private static final double MAX_ABS_LOG_RETURN = Math.log(1.10);

    private final DailyPriceRepository dailyPriceRepository;
    private final CorporateActionFilingDateService corporateActionFilingDateService;

    public PriceInferredExDateEstimator(
            DailyPriceRepository dailyPriceRepository,
            CorporateActionFilingDateService corporateActionFilingDateService) {
        this.dailyPriceRepository = dailyPriceRepository;
        this.corporateActionFilingDateService = corporateActionFilingDateService;
    }

    /**
     * Returns {@code events} with every regular {@code SYNTHETIC} ex-date replaced by its
     * price-inferred date when the ticker's offset fit is trusted; otherwise returns them unchanged.
     * Specials and filing-grounded dates are never touched.
     */
    public List<EquityCorporateActionService.DividendEvent> redateSyntheticEvents(
            String ticker, List<EquityCorporateActionService.DividendEvent> events) {
        if (events.stream().noneMatch(PriceInferredExDateEstimator::isRedatable)) {
            return events;
        }
        List<EquityCorporateActionService.DividendEvent> regular = events.stream()
                .filter(e -> !e.specialEvent())
                .toList();
        OptionalInt offset = estimateRecordOffset(ticker, regular, dailyPriceRepository.findByTickerOrderByTradeDateAsc(ticker));
        if (offset.isEmpty()) {
            return events;
        }
        List<EquityCorporateActionService.DividendEvent> out = new ArrayList<>(events.size());
        for (EquityCorporateActionService.DividendEvent event : events) {
            if (!isRedatable(event)) {
                out.add(event);
                continue;
            }
            out.add(new EquityCorporateActionService.DividendEvent(
                    event.fiscalPeriodEnd(),
                    exDateForOffset(event.fiscalPeriodEnd(), offset.getAsInt()),
                    event.rawAmount(),
                    event.adjustedAmount(),
                    event.year(),
                    false,
                    null,
                    null,
                    CorporateAction.EX_DATE_SOURCE_PRICE_INFERRED));
        }
        return out;
    }

    /**
     * Fits the record-date offset across {@code events} (all regular events of the ticker,
     * whatever their date source: the offset is an issuer habit, and grounded events sharpen it).
     */
    OptionalInt estimateRecordOffset(
            String ticker,
            List<EquityCorporateActionService.DividendEvent> events,
            List<DailyPrice> pricesAsc) {
        Map<LocalDate, DailyReturn> returnsByDate = closeToCloseReturns(pricesAsc);
        if (returnsByDate.isEmpty()) {
            return OptionalInt.empty();
        }
        int bestOffset = -1;
        double bestGain = Double.NEGATIVE_INFINITY;
        double bestEnergy = 0.0;
        int bestObserved = 0;
        for (int offset = MIN_RECORD_OFFSET_DAYS; offset <= MAX_RECORD_OFFSET_DAYS; offset++) {
            double gain = 0.0;
            double energy = 0.0;
            int observed = 0;
            for (EquityCorporateActionService.DividendEvent event : events) {
                DailyReturn daily = returnsByDate.get(exDateForOffset(event.fiscalPeriodEnd(), offset));
                if (daily == null || event.rawAmount() <= 0 || event.rawAmount() >= daily.prevClose()) {
                    continue;
                }
                double expected = Math.log(1.0 - event.rawAmount() / daily.prevClose());
                double r = daily.logReturn();
                gain += r * r - (r - expected) * (r - expected);
                energy += expected * expected;
                observed++;
            }
            if (gain > bestGain) {
                bestGain = gain;
                bestOffset = offset;
                bestEnergy = energy;
                bestObserved = observed;
            }
        }
        if (bestObserved < MIN_OBSERVED_EVENTS || bestEnergy <= 0 || bestGain < MIN_FIT_RATIO * bestEnergy) {
            log.info("[{}] Price-inferred ex-date fit rejected: best record offset {}d, {} observed events, fit ratio {}",
                    ticker, bestOffset, bestObserved, bestEnergy > 0 ? round3(bestGain / bestEnergy) : "n/a");
            return OptionalInt.empty();
        }
        log.info("[{}] Price-inferred record offset {}d after period end ({} observed events, fit ratio {})",
                ticker, bestOffset, bestObserved, round3(bestGain / bestEnergy));
        return OptionalInt.of(bestOffset);
    }

    private LocalDate exDateForOffset(LocalDate periodEnd, int offset) {
        LocalDate recordDate = corporateActionFilingDateService.businessDayOnOrBefore(periodEnd.plusDays(offset));
        return corporateActionFilingDateService.computeExDividendDate(recordDate);
    }

    /** Raw close-to-close log return keyed by trade date, with the prior close it is measured from. */
    private static Map<LocalDate, DailyReturn> closeToCloseReturns(List<DailyPrice> pricesAsc) {
        Map<LocalDate, DailyReturn> out = new HashMap<>();
        Double prevClose = null;
        for (DailyPrice dp : pricesAsc) {
            Double close = dp.getClosePrice();
            if (close == null || close <= 0) {
                continue;
            }
            if (prevClose != null) {
                double r = Math.log(close / prevClose);
                r = Math.max(-MAX_ABS_LOG_RETURN, Math.min(MAX_ABS_LOG_RETURN, r));
                out.put(dp.getTradeDate(), new DailyReturn(prevClose, r));
            }
            prevClose = close;
        }
        return out;
    }

    private static boolean isRedatable(EquityCorporateActionService.DividendEvent event) {
        return !event.specialEvent() && CorporateAction.EX_DATE_SOURCE_SYNTHETIC.equals(event.exDateSource());
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private record DailyReturn(double prevClose, double logReturn) {
    }
}
