package com.fattorestreet.sec_api.corporateaction.support;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts amount-anchored dividend declaration tuples from 8-K / press-release text:
 * a per-share dollar amount plus the record / payable / declaration / ex-dividend dates
 * stated near it. Anchoring dates to the specific amount lets the ex-date assigner match
 * a declaration to the right quarterly XBRL event instead of guessing from cadence priors.
 *
 * <p>Pure text-in/records-out; no HTTP. Input text is expected raw (HTML allowed) and is
 * normalized via {@link FilingTextDates#toSearchableText}.
 */
public class DividendDeclarationTupleExtractor {

    private static final Logger log = LoggerFactory.getLogger(DividendDeclarationTupleExtractor.class);

    /** Chars scanned on each side of an amount anchor for labeled dates. */
    private static final int WINDOW_RADIUS = 600;
    private static final double MAX_PLAUSIBLE_AMOUNT = 100.0;

    private static final String AMOUNT = "\\$\\s*(\\d{0,4}\\.\\d{2,5})";
    /** "$0.24 per share", "$0.24 per common share", "$0.24/share". */
    private static final Pattern AMOUNT_PER_SHARE = Pattern.compile(
            "(?is)" + AMOUNT + "\\s*(?:per\\s+(?:common\\s+|ordinary\\s+|class\\s+\\w+\\s+)?share|/\\s*share)");
    /** "dividend of $0.24", "distribution of $0.24". */
    private static final Pattern DIVIDEND_OF_AMOUNT = Pattern.compile(
            "(?is)(?:dividend|distribution)\\s+of\\s+" + AMOUNT);
    /** Label-first table phrasing: "Dividend per share: $1.02". */
    private static final Pattern PER_SHARE_LABEL_AMOUNT = Pattern.compile(
            "(?is)(?:dividend|distribution)\\s+per\\s+share[^$]{0,40}?" + AMOUNT);

    private static final Pattern RECORD_DATE = Pattern.compile(
            "(?is)(?:record\\s+date|(?:share|stock)?holders?\\s+of\\s+record|record\\s+at\\s+the\\s+close\\s+of\\s+business)"
                    + "[^$]{0,160}?(" + FilingTextDates.DATE_PATTERN + ")");
    private static final Pattern PAYABLE_DATE = Pattern.compile(
            "(?is)(?:payable|payment\\s+date|paid\\s+on|to\\s+be\\s+paid)"
                    + "[^$]{0,160}?(" + FilingTextDates.DATE_PATTERN + ")");
    private static final Pattern DECLARATION_DATE = Pattern.compile(
            "(?is)(?:declared|declaration\\s+date|announced)"
                    + "[^$]{0,160}?(" + FilingTextDates.DATE_PATTERN + ")");
    private static final Pattern EX_DATE = Pattern.compile(
            "(?is)ex-?dividend(?:\\s+(?:date|dt))?[^$]{0,160}?(" + FilingTextDates.DATE_PATTERN + ")");

    private final long regexBudgetMillis;

    public DividendDeclarationTupleExtractor() {
        this(BoundedRegexInput.DEFAULT_BUDGET_MILLIS);
    }

    /** Visible for testing: lets a test force the timeout path without a multi-second input. */
    DividendDeclarationTupleExtractor(long regexBudgetMillis) {
        this.regexBudgetMillis = regexBudgetMillis;
    }

    /**
     * @param amountPerShare declared per-share cash amount (as stated at declaration time)
     * @param exDate only set when the filing states the ex-dividend date explicitly
     */
    public record DividendDeclaration(
            double amountPerShare,
            LocalDate declarationDate,
            LocalDate recordDate,
            LocalDate payableDate,
            LocalDate exDate,
            LocalDate filingDate,
            String accessionNumber,
            int confidenceScore) {
    }

    public List<DividendDeclaration> extract(String htmlOrText, LocalDate filingDate, String accessionNumber) {
        String searchable = FilingTextDates.toSearchableText(htmlOrText);
        if (searchable.isBlank()) {
            return List.of();
        }
        List<AmountAnchor> anchors = findAmountAnchors(searchable);
        Map<String, DividendDeclaration> bestByKey = new LinkedHashMap<>();
        for (AmountAnchor anchor : anchors) {
            int windowStart = Math.max(0, anchor.position() - WINDOW_RADIUS);
            int windowEnd = Math.min(searchable.length(), anchor.position() + WINDOW_RADIUS);
            String window = searchable.substring(windowStart, windowEnd);
            int anchorInWindow = anchor.position() - windowStart;

            LocalDate recordDate = closestLabeledDate(window, RECORD_DATE, anchorInWindow);
            if (recordDate == null) {
                continue;
            }
            LocalDate payableDate = closestLabeledDate(window, PAYABLE_DATE, anchorInWindow);
            LocalDate declarationDate = closestLabeledDate(window, DECLARATION_DATE, anchorInWindow);
            LocalDate exDate = closestLabeledDate(window, EX_DATE, anchorInWindow);

            int score = 60;
            if (anchor.perSharePhrasing()) {
                score += 15;
            }
            if (payableDate != null) {
                score += 10;
            }
            if (declarationDate != null || exDate != null) {
                score += 10;
            }
            DividendDeclaration declaration = new DividendDeclaration(
                    anchor.amount(), declarationDate, recordDate, payableDate, exDate,
                    filingDate, accessionNumber, Math.min(score, 100));
            String key = tupleKey(anchor.amount(), recordDate);
            DividendDeclaration current = bestByKey.get(key);
            if (current == null || declaration.confidenceScore() > current.confidenceScore()) {
                bestByKey.put(key, declaration);
            }
        }
        return new ArrayList<>(bestByKey.values());
    }

    /** Stable key for deduplicating the same declaration across documents/filings. */
    public static String tupleKey(double amount, LocalDate recordDate) {
        return String.format(Locale.US, "%.4f", amount) + "|" + recordDate;
    }

    private List<AmountAnchor> findAmountAnchors(String searchable) {
        List<AmountAnchor> anchors = new ArrayList<>();
        collectAnchors(searchable, AMOUNT_PER_SHARE, true, anchors);
        collectAnchors(searchable, DIVIDEND_OF_AMOUNT, false, anchors);
        collectAnchors(searchable, PER_SHARE_LABEL_AMOUNT, true, anchors);
        return anchors;
    }

    private void collectAnchors(String searchable, Pattern pattern, boolean perSharePhrasing, List<AmountAnchor> out) {
        Matcher matcher = pattern.matcher(searchable);
        while (matcher.find()) {
            double amount;
            try {
                amount = Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                continue;
            }
            if (amount <= 0 || amount >= MAX_PLAUSIBLE_AMOUNT) {
                continue;
            }
            out.add(new AmountAnchor(amount, matcher.start(), perSharePhrasing));
        }
    }

    /** Best labeled date in the window: nearest label occurrence to the amount anchor. */
    private LocalDate closestLabeledDate(String window, Pattern labelPattern, int anchorInWindow) {
        // All four date patterns funnel through here, and each pairs a lazy bounded quantifier
        // with the month-name alternation FindSecBugs flags as REDOS. Fresh budget per call so
        // one pathological window cannot starve the others.
        Matcher matcher = labelPattern.matcher(BoundedRegexInput.of(window, regexBudgetMillis));
        LocalDate best = null;
        int bestDistance = Integer.MAX_VALUE;
        try {
            while (matcher.find()) {
                LocalDate parsed = FilingTextDates.parseUsDate(matcher.group(1)).orElse(null);
                if (parsed == null) {
                    continue;
                }
                int distance = Math.abs(matcher.start() - anchorInWindow);
                if (distance < bestDistance) {
                    best = parsed;
                    bestDistance = distance;
                }
            }
        } catch (BoundedRegexInput.RegexTimeoutException e) {
            // Keep the nearest date found so far. Same outcome as a window holding no further
            // labeled dates, so one slow document never aborts the extraction.
            log.warn("Labeled-date pattern timed out over {} chars; keeping nearest match so far ({}): {}",
                    window.length(), best, e.getMessage());
        }
        return best;
    }

    private record AmountAnchor(double amount, int position, boolean perSharePhrasing) {
    }
}
