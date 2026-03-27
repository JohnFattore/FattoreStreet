package com.fattorestreet.sec_api.corporateaction.support;

import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.util.SecTextUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class EtfIdentityEvaluator {

    public IdentitySignal evaluateIdentity(
            String filingText,
            Listing listing,
            String ticker,
            String formType,
            String documentName) {
        String normalized = SecTextUtils.normalizeText(filingText);
        int score = 0;
        List<String> matchedSignals = new ArrayList<>();

        if (SecTextUtils.containsToken(normalized, SecTextUtils.normalizeText(ticker))) {
            score += 2;
            matchedSignals.add("ticker");
        }
        if (SecTextUtils.notBlank(listing.getSecClassTicker())
                && SecTextUtils.containsToken(normalized, SecTextUtils.normalizeText(listing.getSecClassTicker()))) {
            score += 3;
            matchedSignals.add("secClassTicker");
        }
        if (SecTextUtils.notBlank(listing.getSecSeriesId()) && normalized.contains(SecTextUtils.normalizeText(listing.getSecSeriesId()))) {
            score += 4;
            matchedSignals.add("secSeriesId");
        }
        if (SecTextUtils.notBlank(listing.getSecClassContractId())
                && normalized.contains(SecTextUtils.normalizeText(listing.getSecClassContractId()))) {
            score += 4;
            matchedSignals.add("secClassContractId");
        }
        if (matchesNameSignal(normalized, listing.getSecSeriesName())) {
            score += 2;
            matchedSignals.add("secSeriesName");
        }
        if (matchesNameSignal(normalized, listing.getSecClassName())) {
            score += 2;
            matchedSignals.add("secClassName");
        }
        if (SecTextUtils.containsToken(SecTextUtils.normalizeText(Objects.toString(documentName, "")), SecTextUtils.normalizeText(ticker))) {
            score += 1;
            matchedSignals.add("documentTickerHint");
        }
        if (SecTextUtils.containsToken(SecTextUtils.normalizeText(Objects.toString(formType, "")), "497")) {
            score += 1;
            matchedSignals.add("formHint");
        }
        return new IdentitySignal(score, matchedSignals);
    }

    private boolean matchesNameSignal(String normalizedText, String name) {
        if (!SecTextUtils.notBlank(name)) {
            return false;
        }
        String normalizedName = SecTextUtils.normalizeText(name);
        if (normalizedName.length() < 6) {
            return false;
        }
        if (normalizedText.contains(normalizedName)) {
            return true;
        }
        String[] words = normalizedName.split("\\s+");
        int matched = 0;
        for (String word : words) {
            if (word.length() < 4) {
                continue;
            }
            if (SecTextUtils.containsToken(normalizedText, word)) {
                matched++;
            }
        }
        return matched >= 2;
    }

    public record IdentitySignal(int score, List<String> matchedSignals) {
    }
}
