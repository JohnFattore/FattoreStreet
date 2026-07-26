package com.fattorestreet.sec_api.corporateaction.support;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fattorestreet.sec_api.corporateaction.CorporateActionFilingDateService;
import com.fattorestreet.sec_api.util.SecDateParsingUtils;
import com.fattorestreet.sec_api.util.SecTextUtils;

@Component
public class EtfDateExtractor {

    private static final Logger log = LoggerFactory.getLogger(EtfDateExtractor.class);

    private static final Pattern LINE_SPLIT = Pattern.compile("\\r?\\n");
    private static final Pattern EX_DIVIDEND_DATE_SENTENCE_PATTERN = Pattern.compile(
            "(?is)(?:ex-?dividend(?:\\s+(?:date|dt))?|ex\\s+(?:date|dt))[^\\n]{0,220}");
    private static final Pattern RECORD_DATE_SENTENCE_PATTERN = Pattern.compile(
            "(?is)(?:record\\s+(?:date|dt)|shareholders?\\s+of\\s+record|holders?\\s+of\\s+record)[^\\n]{0,260}");
    private static final Pattern PAY_DATE_SENTENCE_PATTERN = Pattern.compile(
            "(?is)(?:payable\\s+(?:date|dt)|payment\\s+(?:date|dt)|pay\\s+date|pay\\s+dt)[^\\n]{0,220}");
    private static final Pattern TABLE_EX_DATE_LINE_PATTERN = Pattern.compile(
            "(?i)\\b(?:ex\\s*(?:-|\\s)?(?:dividend\\s*)?(?:date|dt)|ex-?dividend\\s+date)\\b");
    private static final Pattern TABLE_RECORD_DATE_LINE_PATTERN = Pattern.compile(
            "(?i)\\b(?:record\\s+(?:date|dt)|holders?\\s+of\\s+record|shareholders?\\s+of\\s+record)\\b");
    private static final Pattern TABLE_PAY_DATE_LINE_PATTERN = Pattern.compile(
            "(?i)\\b(?:payable\\s+(?:date|dt)|payment\\s+(?:date|dt)|pay\\s+(?:date|dt))\\b");
    private static final Pattern DISTRIBUTION_KEYWORD_PATTERN = Pattern.compile(
            "(?i)\\b(?:dividend|distribution|cash\\s+distribution|per\\s+share)\\b");
    private static final Pattern AMOUNT_TOKEN_PATTERN = Pattern.compile("\\$\\s*\\d+(?:\\.\\d+)?");
    private static final Pattern ANNUAL_ARTIFACT_PATTERN = Pattern.compile(
            "(?i)\\b(?:year\\s+ended|fiscal\\s+year|for\\s+the\\s+year|annual)\\b");
    private static final Pattern LABELED_DATE_PATTERN = Pattern.compile(
            "(?is)(ex(?:-|\\s)?(?:dividend)?(?:\\s+(?:date|dt))?|record\\s+(?:date|dt)|shareholders?\\s+of\\s+record|holders?\\s+of\\s+record|pay(?:able|ment)?\\s+(?:date|dt))[^\\n\\r\\d]{0,50}"
                    + "(\\d{4}-\\d{2}-\\d{2}|\\d{4}/\\d{1,2}/\\d{1,2}|\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{1,2}-\\d{1,2}-\\d{4}|"
                    + "(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{1,2},?\\s+\\d{4})");

    private final CorporateActionFilingDateService corporateActionFilingDateService;

    public EtfDateExtractor(CorporateActionFilingDateService corporateActionFilingDateService) {
        this.corporateActionFilingDateService = corporateActionFilingDateService;
    }

    public EtfDateSignals extractEtfDateSignals(String filingText, LocalDate filingDate) {
        return extractEtfDateSignals(filingText, filingDate, BoundedRegexInput.DEFAULT_BUDGET_MILLIS);
    }

    /** Visible for testing: lets a test force the timeout path without a multi-second input. */
    EtfDateSignals extractEtfDateSignals(String filingText, LocalDate filingDate, long regexBudgetMillis) {
        String normalizedDateText = SecTextUtils.normalizeDateExtractionText(filingText);
        List<DateCandidate> exCandidates = extractDateCandidates(
                normalizedDateText,
                EX_DIVIDEND_DATE_SENTENCE_PATTERN,
                "ex");
        List<DateCandidate> recordCandidates = extractDateCandidates(
                normalizedDateText,
                RECORD_DATE_SENTENCE_PATTERN,
                "record");
        List<DateCandidate> payCandidates = extractDateCandidates(
                normalizedDateText,
                PAY_DATE_SENTENCE_PATTERN,
                "pay");
        collectLabeledDateCandidates(
                normalizedDateText, exCandidates, recordCandidates, payCandidates, regexBudgetMillis);
        collectTableDateCandidates(normalizedDateText, exCandidates, recordCandidates, payCandidates);

        DateCandidate exBest = bestDate(exCandidates, filingDate);
        DateCandidate recordBest = bestDate(recordCandidates, filingDate);
        DateCandidate payBest = bestDate(payCandidates, filingDate);
        LocalDate exDate = exBest != null ? exBest.date : null;
        LocalDate recordDate = recordBest != null ? recordBest.date : null;
        LocalDate payDate = payBest != null ? payBest.date : null;

        LocalDate effectiveDate = null;
        int confidence = 0;
        String resolutionPath = "none";
        if (exDate != null) {
            effectiveDate = exDate;
            confidence = 95;
            resolutionPath = "ex_date";
        } else if (recordDate != null) {
            effectiveDate = corporateActionFilingDateService.computeExDividendDate(recordDate);
            if (effectiveDate != null) {
                confidence = 86;
                resolutionPath = "record_date";
            }
        } else if (payDate != null) {
            effectiveDate = SecDateParsingUtils.previousBusinessDay(payDate);
            confidence = 62;
            resolutionPath = "pay_date_fallback";
        } else if (filingDate != null) {
            effectiveDate = SecDateParsingUtils.previousBusinessDay(filingDate);
            confidence = 55;
            resolutionPath = "filing_date_fallback";
        }
        if (effectiveDate == null) {
            return null;
        }

        if (payDate != null) {
            confidence = Math.min(confidence + 5, 100);
        }
        return new EtfDateSignals(
                effectiveDate,
                recordDate,
                payDate,
                confidence,
                resolutionPath,
                exBest != null ? exBest.source() : null,
                recordBest != null ? recordBest.source() : null,
                payBest != null ? payBest.source() : null);
    }

    private List<DateCandidate> extractDateCandidates(String filingText, Pattern sentencePattern, String label) {
        List<DateCandidate> candidates = new ArrayList<>();
        Matcher sentenceMatcher = sentencePattern.matcher(filingText);
        while (sentenceMatcher.find()) {
            String snippet = sentenceMatcher.group();
            for (LocalDate parsed : SecDateParsingUtils.extractAllDates(snippet)) {
                int score = 82;
                if (snippet.contains("-") && snippet.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
                    score = 90;
                } else if (snippet.matches("(?is).*(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec).*")) {
                    score = 88;
                }
                candidates.add(new DateCandidate(parsed, score, label + "_sentence"));
            }
        }
        return candidates;
    }

    private void collectLabeledDateCandidates(
            String filingText,
            List<DateCandidate> exCandidates,
            List<DateCandidate> recordCandidates,
            List<DateCandidate> payCandidates,
            long regexBudgetMillis) {
        // The one pattern here FindSecBugs flags as REDOS, and the only one in this class matched
        // against the whole filing rather than a line or a window, so it is the one that needs
        // the guard.
        Matcher labeled = LABELED_DATE_PATTERN.matcher(BoundedRegexInput.of(filingText, regexBudgetMillis));
        try {
            while (labeled.find()) {
                String label = Objects.toString(labeled.group(1), "").toLowerCase(Locale.US);
                String rawDate = Objects.toString(labeled.group(2), "");
                LocalDate parsed = SecDateParsingUtils.parseAnyDate(rawDate);
                if (parsed == null) {
                    continue;
                }
                DateCandidate candidate = new DateCandidate(parsed, 92, "labeled_pattern");
                if (label.contains("ex")) {
                    exCandidates.add(candidate);
                } else if (label.contains("record") || label.contains("holders") || label.contains("shareholder")) {
                    recordCandidates.add(candidate);
                } else if (label.contains("pay")) {
                    payCandidates.add(candidate);
                }
            }
        } catch (BoundedRegexInput.RegexTimeoutException e) {
            // Keep the candidates already collected. The sentence and table passes still ran, so
            // a slow document degrades this signal rather than losing the whole extraction.
            log.warn("Labeled-date pattern timed out over {} chars; keeping {} ex / {} record / {} pay so far: {}",
                    filingText.length(), exCandidates.size(), recordCandidates.size(), payCandidates.size(),
                    e.getMessage());
        }
    }

    private void collectTableDateCandidates(
            String filingText,
            List<DateCandidate> exCandidates,
            List<DateCandidate> recordCandidates,
            List<DateCandidate> payCandidates) {
        String[] lines = LINE_SPLIT.split(filingText, -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }
            String window = line;
            if (i + 1 < lines.length) {
                window += " " + lines[i + 1];
            }
            if (i + 2 < lines.length) {
                window += " " + lines[i + 2];
            }
            List<LocalDate> dates = SecDateParsingUtils.extractAllDates(window);
            if (dates.isEmpty()) {
                continue;
            }
            boolean hasDistributionSignal = DISTRIBUTION_KEYWORD_PATTERN.matcher(window).find()
                    || AMOUNT_TOKEN_PATTERN.matcher(window).find();
            boolean annualArtifact = ANNUAL_ARTIFACT_PATTERN.matcher(window).find();
            if (annualArtifact && !hasExplicitDateLabel(window)) {
                continue;
            }
            if (TABLE_EX_DATE_LINE_PATTERN.matcher(line).find()) {
                if (!hasDistributionSignal) {
                    continue;
                }
                for (LocalDate date : dates) {
                    exCandidates.add(new DateCandidate(date, 80, "table_line_ex"));
                }
            }
            if (TABLE_RECORD_DATE_LINE_PATTERN.matcher(line).find()) {
                if (!hasDistributionSignal) {
                    continue;
                }
                for (LocalDate date : dates) {
                    recordCandidates.add(new DateCandidate(date, 80, "table_line_record"));
                }
            }
            if (TABLE_PAY_DATE_LINE_PATTERN.matcher(line).find()) {
                if (!hasDistributionSignal) {
                    continue;
                }
                for (LocalDate date : dates) {
                    payCandidates.add(new DateCandidate(date, 78, "table_line_pay"));
                }
            }
        }
    }

    private boolean hasExplicitDateLabel(String text) {
        return TABLE_EX_DATE_LINE_PATTERN.matcher(text).find()
                || TABLE_RECORD_DATE_LINE_PATTERN.matcher(text).find()
                || TABLE_PAY_DATE_LINE_PATTERN.matcher(text).find();
    }

    private DateCandidate bestDate(List<DateCandidate> candidates, LocalDate filingDate) {
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort((left, right) -> {
            int scoreCmp = Integer.compare(right.score(), left.score());
            if (scoreCmp != 0) {
                return scoreCmp;
            }
            if (filingDate != null) {
                long leftGap = Math.abs(ChronoUnit.DAYS.between(filingDate, left.date()));
                long rightGap = Math.abs(ChronoUnit.DAYS.between(filingDate, right.date()));
                int gapCmp = Long.compare(leftGap, rightGap);
                if (gapCmp != 0) {
                    return gapCmp;
                }
            }
            return left.date().compareTo(right.date());
        });
        return candidates.get(0);
    }

    private record DateCandidate(LocalDate date, int score, String source) {
    }

    public record EtfDateSignals(
            LocalDate effectiveDate,
            LocalDate recordDate,
            LocalDate payDate,
            int confidenceScore,
            String resolutionPath,
            String exSource,
            String recordSource,
            String paySource) {
    }
}
