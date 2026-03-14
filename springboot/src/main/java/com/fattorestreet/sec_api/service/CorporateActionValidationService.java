package com.fattorestreet.sec_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CorporateActionValidationService {

    private static final Logger log = LoggerFactory.getLogger(CorporateActionValidationService.class);
    private static final int MAX_SAMPLE_ROWS = 20;
    private static final long DIVIDEND_DATE_TOLERANCE_DAYS = 5;
    private static final long SPLIT_DATE_TOLERANCE_DAYS = 7;
    private static final double DIVIDEND_AMOUNT_TOLERANCE = 0.01;
    private static final double SPLIT_RATIO_TOLERANCE = 0.01;

    private final CorporateActionRepository corporateActionRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CorporateActionValidationService(CorporateActionRepository corporateActionRepository, ObjectMapper objectMapper) {
        this.corporateActionRepository = corporateActionRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().build();
    }

    public ValidationReport validateTicker(String ticker, LocalDate minDateInclusive) {
        List<NormalizedEvent> secEvents = loadSecEvents(ticker, minDateInclusive);
        List<NormalizedEvent> yfEvents = loadYahooReferenceEvents(ticker, minDateInclusive);
        return diffEvents(ticker, secEvents, yfEvents);
    }

    public Map<String, Object> summarizeBatch(List<ValidationReport> reports) {
        int totalTickers = reports.size();
        int totalSecEvents = 0;
        int totalYfEvents = 0;
        int missingInSec = 0;
        int extraInSec = 0;
        int dateDrift = 0;
        int amountDrift = 0;
        List<Map<String, Object>> sample = new ArrayList<>();
        for (ValidationReport report : reports) {
            totalSecEvents += report.secEventCount();
            totalYfEvents += report.yfEventCount();
            missingInSec += report.missingInSec();
            extraInSec += report.extraInSec();
            dateDrift += report.dateDrift();
            amountDrift += report.amountDrift();
            if (sample.size() < MAX_SAMPLE_ROWS) {
                for (Map<String, Object> row : report.mismatchSamples()) {
                    if (sample.size() >= MAX_SAMPLE_ROWS) {
                        break;
                    }
                    sample.add(row);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tickersValidated", totalTickers);
        out.put("secEvents", totalSecEvents);
        out.put("yfinanceEvents", totalYfEvents);
        out.put("missingInSec", missingInSec);
        out.put("extraInSec", extraInSec);
        out.put("dateDrift", dateDrift);
        out.put("amountDrift", amountDrift);
        out.put("mismatchRate", totalYfEvents == 0 ? 0.0 : round4((missingInSec + dateDrift + amountDrift) / (double) totalYfEvents));
        out.put("sampleMismatches", sample);
        return out;
    }

    private List<NormalizedEvent> loadSecEvents(String ticker, LocalDate minDateInclusive) {
        List<CorporateAction> actions = corporateActionRepository.findByTicker(ticker);
        List<NormalizedEvent> out = new ArrayList<>();
        for (CorporateAction action : actions) {
            if (action.getEffectiveDate() == null || action.getEffectiveDate().isBefore(minDateInclusive)) {
                continue;
            }
            if (action.getActionType() == ActionType.DIVIDEND) {
                double amount = action.getAdjustedDividend() != null ? action.getAdjustedDividend() : action.getRatio();
                if (amount > 0) {
                    out.add(new NormalizedEvent(ActionType.DIVIDEND, action.getEffectiveDate(), round4(amount), "sec"));
                }
            } else if (action.getActionType() == ActionType.SPLIT) {
                if (action.getRatio() != null && action.getRatio() > 0) {
                    out.add(new NormalizedEvent(ActionType.SPLIT, action.getEffectiveDate(), round4(action.getRatio()), "sec"));
                }
            }
        }
        out.sort(Comparator.comparing(NormalizedEvent::date).thenComparing(NormalizedEvent::value));
        return out;
    }

    private List<NormalizedEvent> loadYahooReferenceEvents(String ticker, LocalDate minDateInclusive) {
        try {
            long period1 = minDateInclusive.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
            long period2 = LocalDate.now().plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
            String url = String.format(
                    Locale.US,
                    "https://query1.finance.yahoo.com/v8/finance/chart/%s?period1=%d&period2=%d&interval=1d&events=div%%2Csplits",
                    ticker,
                    period1,
                    period2);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("[{}] yfinance validation request failed with status {}", ticker, response.statusCode());
                return List.of();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode resultNode = root.path("chart").path("result");
            if (!resultNode.isArray() || resultNode.isEmpty()) {
                return List.of();
            }
            JsonNode eventsNode = resultNode.get(0).path("events");
            List<NormalizedEvent> out = new ArrayList<>();
            parseYahooDividends(eventsNode.path("dividends"), minDateInclusive, out);
            parseYahooSplits(eventsNode.path("splits"), minDateInclusive, out);
            out.sort(Comparator.comparing(NormalizedEvent::date).thenComparing(NormalizedEvent::value));
            return out;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("[{}] yfinance validation fetch failed: {}", ticker, e.getMessage());
            return List.of();
        }
    }

    private void parseYahooDividends(JsonNode dividendsNode, LocalDate minDateInclusive, List<NormalizedEvent> out) {
        if (!dividendsNode.isObject()) {
            return;
        }
        dividendsNode.fields().forEachRemaining(entry -> {
            JsonNode row = entry.getValue();
            long epoch = row.path("date").asLong(0L);
            double amount = row.path("amount").asDouble(0.0);
            LocalDate date = toDate(epoch);
            if (date != null && !date.isBefore(minDateInclusive) && amount > 0) {
                out.add(new NormalizedEvent(ActionType.DIVIDEND, date, round4(amount), "yfinance"));
            }
        });
    }

    private void parseYahooSplits(JsonNode splitsNode, LocalDate minDateInclusive, List<NormalizedEvent> out) {
        if (!splitsNode.isObject()) {
            return;
        }
        splitsNode.fields().forEachRemaining(entry -> {
            JsonNode row = entry.getValue();
            long epoch = row.path("date").asLong(0L);
            double numerator = row.path("numerator").asDouble(0.0);
            double denominator = row.path("denominator").asDouble(0.0);
            LocalDate date = toDate(epoch);
            if (date == null || date.isBefore(minDateInclusive)) {
                return;
            }
            if (numerator > 0 && denominator > 0) {
                double ratio = round4(denominator / numerator);
                out.add(new NormalizedEvent(ActionType.SPLIT, date, ratio, "yfinance"));
            }
        });
    }

    private LocalDate toDate(long epochSeconds) {
        if (epochSeconds <= 0) {
            return null;
        }
        return java.time.Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDate();
    }

    private ValidationReport diffEvents(String ticker, List<NormalizedEvent> secEvents, List<NormalizedEvent> yfEvents) {
        List<Map<String, Object>> samples = new ArrayList<>();
        int missingInSec = 0;
        int extraInSec = 0;
        int dateDrift = 0;
        int amountDrift = 0;

        Set<Integer> matchedYfIndexes = new LinkedHashSet<>();
        for (NormalizedEvent secEvent : secEvents) {
            MatchCandidate candidate = findBestMatch(secEvent, yfEvents, matchedYfIndexes);
            if (candidate == null) {
                extraInSec++;
                addSample(samples, ticker, "extra_in_sec", "medium", secEvent, null);
                continue;
            }
            matchedYfIndexes.add(candidate.index());
            long dateGap = Math.abs(ChronoUnit.DAYS.between(secEvent.date(), candidate.event().date()));
            double amountGap = Math.abs(secEvent.value() - candidate.event().value());
            if (dateGap > 0) {
                dateDrift++;
                addSample(samples, ticker, "date_drift", dateGap > 3 ? "medium" : "low", secEvent, candidate.event());
            }
            if (amountGap > toleranceFor(secEvent.type())) {
                amountDrift++;
                addSample(samples, ticker, "amount_drift", "high", secEvent, candidate.event());
            }
        }

        for (int i = 0; i < yfEvents.size(); i++) {
            if (matchedYfIndexes.contains(i)) {
                continue;
            }
            missingInSec++;
            addSample(samples, ticker, "missing_in_sec", "high", null, yfEvents.get(i));
        }

        return new ValidationReport(
                ticker,
                secEvents.size(),
                yfEvents.size(),
                missingInSec,
                extraInSec,
                dateDrift,
                amountDrift,
                samples);
    }

    private MatchCandidate findBestMatch(
            NormalizedEvent secEvent,
            List<NormalizedEvent> yfEvents,
            Set<Integer> matchedYfIndexes) {
        MatchCandidate best = null;
        for (int i = 0; i < yfEvents.size(); i++) {
            if (matchedYfIndexes.contains(i)) {
                continue;
            }
            NormalizedEvent candidate = yfEvents.get(i);
            if (candidate.type() != secEvent.type()) {
                continue;
            }
            long maxDateGap = secEvent.type() == ActionType.SPLIT ? SPLIT_DATE_TOLERANCE_DAYS : DIVIDEND_DATE_TOLERANCE_DAYS;
            long dateGap = Math.abs(ChronoUnit.DAYS.between(secEvent.date(), candidate.date()));
            if (dateGap > maxDateGap) {
                continue;
            }
            double amountGap = Math.abs(secEvent.value() - candidate.value());
            double score = dateGap * 100 + amountGap;
            if (best == null || score < best.score()) {
                best = new MatchCandidate(i, candidate, score);
            }
        }
        return best;
    }

    private void addSample(
            List<Map<String, Object>> samples,
            String ticker,
            String category,
            String severity,
            NormalizedEvent sec,
            NormalizedEvent yf) {
        if (samples.size() >= MAX_SAMPLE_ROWS) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ticker", ticker);
        row.put("category", category);
        row.put("severity", severity);
        row.put("eventType", sec != null ? sec.type().name() : (yf != null ? yf.type().name() : null));
        row.put("secDate", sec != null ? sec.date().toString() : null);
        row.put("secValue", sec != null ? sec.value() : null);
        row.put("yfinanceDate", yf != null ? yf.date().toString() : null);
        row.put("yfinanceValue", yf != null ? yf.value() : null);
        samples.add(row);
    }

    private double toleranceFor(ActionType type) {
        if (type == ActionType.SPLIT) {
            return SPLIT_RATIO_TOLERANCE;
        }
        return DIVIDEND_AMOUNT_TOLERANCE;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record NormalizedEvent(ActionType type, LocalDate date, double value, String source) {
    }

    private record MatchCandidate(int index, NormalizedEvent event, double score) {
    }

    public record ValidationReport(
            String ticker,
            int secEventCount,
            int yfEventCount,
            int missingInSec,
            int extraInSec,
            int dateDrift,
            int amountDrift,
            List<Map<String, Object>> mismatchSamples) {
        public Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ticker", ticker);
            out.put("secEventCount", secEventCount);
            out.put("yfinanceEventCount", yfEventCount);
            out.put("missingInSec", missingInSec);
            out.put("extraInSec", extraInSec);
            out.put("dateDrift", dateDrift);
            out.put("amountDrift", amountDrift);
            out.put("mismatchSamples", mismatchSamples);
            return out;
        }
    }
}
