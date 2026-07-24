package com.fattorestreet.sec_api.corporateaction.support;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;

@Component
public class EtfActionPersister {

    private final CorporateActionRepository corporateActionRepository;

    public EtfActionPersister(CorporateActionRepository corporateActionRepository) {
        this.corporateActionRepository = corporateActionRepository;
    }

    public PersistResult persistDividend(
            String ticker,
            Listing listing,
            String formType,
            String accessionNumber,
            LocalDate effectiveDate,
            double amount,
            EtfDateExtractor.EtfDateSignals dateSignals) {
        Optional<CorporateAction> existing = corporateActionRepository
                .findByTickerAndActionTypeAndEffectiveDateAndRatioAndSourceType(
                        ticker,
                        CorporateAction.ActionType.DIVIDEND,
                        effectiveDate,
                        amount,
                        CorporateAction.SourceType.SEC_ETF_FILING);
        if (existing.isPresent()) {
            return PersistResult.DUPLICATE;
        }

        CorporateAction action = new CorporateAction();
        action.setTicker(ticker);
        action.setActionType(CorporateAction.ActionType.DIVIDEND);
        action.setEffectiveDate(effectiveDate);
        action.setRatio(amount);
        action.setRawDividend(amount);
        action.setAdjustedDividend(amount);
        action.setSourceType(CorporateAction.SourceType.SEC_ETF_FILING);
        action.setFormType(formType);
        action.setAccessionNumber(accessionNumber);
        action.setRecordDate(dateSignals.recordDate());
        action.setPayDate(dateSignals.payDate());
        action.setConfidenceScore((double) dateSignals.confidenceScore());
        action.setSecSeriesId(listing.getSecSeriesId());
        action.setSecClassContractId(listing.getSecClassContractId());
        corporateActionRepository.save(action);
        return PersistResult.SAVED;
    }

    public enum PersistResult {
        SAVED,
        DUPLICATE
    }
}
