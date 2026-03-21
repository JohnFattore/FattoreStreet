package com.fattorestreet.sec_api.service;

import com.fattorestreet.sec_api.util.SecNumberUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EtfAmountExtractor {

    private static final Pattern DIVIDEND_AMOUNT_PATTERN = Pattern.compile(
            "(?is)(?:cash\\s+)?(?:distribution|dividend)[^$\\d]{0,80}\\$\\s*([0-9]+(?:\\.[0-9]{1,6})?)");
    private static final Pattern PER_SHARE_AMOUNT_PATTERN = Pattern.compile(
            "(?is)\\$\\s*([0-9]+(?:\\.[0-9]{1,6})?)\\s+per\\s+share");
    private static final Pattern TABLE_LINE_AMOUNT_PATTERN = Pattern.compile(
            "(?i)(?:dividend|distribution)[^\\n$]{0,80}\\$\\s*([0-9]+(?:\\.[0-9]{1,6})?)");

    public AmountCandidate extractDividendAmount(String filingText) {
        List<AmountCandidate> candidates = new ArrayList<>();
        Matcher direct = DIVIDEND_AMOUNT_PATTERN.matcher(filingText);
        while (direct.find()) {
            Double parsed = SecNumberUtils.parsePositiveDouble(direct.group(1));
            if (parsed != null && parsed < 50) {
                candidates.add(new AmountCandidate(parsed, 90, "dividend_amount_pattern"));
            }
        }

        Matcher perShare = PER_SHARE_AMOUNT_PATTERN.matcher(filingText);
        while (perShare.find()) {
            Double parsed = SecNumberUtils.parsePositiveDouble(perShare.group(1));
            if (parsed != null && parsed < 50) {
                candidates.add(new AmountCandidate(parsed, 85, "per_share_pattern"));
            }
        }

        Matcher tableLine = TABLE_LINE_AMOUNT_PATTERN.matcher(filingText);
        while (tableLine.find()) {
            Double parsed = SecNumberUtils.parsePositiveDouble(tableLine.group(1));
            if (parsed != null && parsed < 50) {
                candidates.add(new AmountCandidate(parsed, 75, "table_line_pattern"));
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparingInt(AmountCandidate::score).reversed());
        return candidates.get(0);
    }

    public record AmountCandidate(Double amount, int score, String source) {
    }
}
