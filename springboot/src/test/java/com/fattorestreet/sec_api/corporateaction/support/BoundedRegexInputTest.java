package com.fattorestreet.sec_api.corporateaction.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedRegexInputTest {

    /** Same shape as the corporate-action date patterns: lazy bounded gap, then an alternation. */
    private static final Pattern LAZY_GAP =
            Pattern.compile("(?is)record\\s+date.{0,900}?((?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?)\\s+\\d{1,2},?\\s+\\d{4})");

    // @spec FILING-046
    @Test
    void matchesIdenticallyToUnguardedInputWhenWithinBudget() {
        String text = "The record date is January 15, 2025 for holders.";

        Matcher unguarded = LAZY_GAP.matcher(text);
        Matcher guarded = LAZY_GAP.matcher(BoundedRegexInput.of(text, 2_000L));

        assertTrue(unguarded.find());
        assertTrue(guarded.find());
        assertEquals(unguarded.group(1), guarded.group(1));
        assertEquals("January 15, 2025", guarded.group(1));
    }

    // @spec FILING-046
    @Test
    void reportsNoMatchIdenticallyWhenWithinBudget() {
        String text = "This filing mentions no record date at all.";

        assertFalse(LAZY_GAP.matcher(text).find());
        assertFalse(LAZY_GAP.matcher(BoundedRegexInput.of(text, 2_000L)).find());
    }

    // @spec FILING-043
    @Test
    void throwsOnceBudgetIsExhausted() throws InterruptedException {
        BoundedRegexInput guarded = BoundedRegexInput.of("a".repeat(8192), 1L);
        Thread.sleep(20L);

        // Reads past the check interval, so the deadline is observed and the read aborts.
        assertThrows(BoundedRegexInput.RegexTimeoutException.class, () -> {
            for (int i = 0; i < 8192; i++) {
                guarded.charAt(i);
            }
        });
    }

    // @spec FILING-042, FILING-043
    @Test
    void abortsSlowMatchOverLargeInputWithinBudget() {
        // The production shape fed text that maximises backtracking: a "record date" anchor every
        // ~900 characters, never followed by a parsable date inside the bounded gap, so each
        // anchor drives the lazy quantifier through its full range against the month alternation.
        String block = "record date " + "x".repeat(900) + " ";
        String input = block.repeat(4000);

        Matcher matcher = LAZY_GAP.matcher(BoundedRegexInput.of(input, 100L));

        long startNanos = System.nanoTime();
        assertThrows(BoundedRegexInput.RegexTimeoutException.class, matcher::find);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        // Generous ceiling: the point is that it terminates promptly, not that it lands exactly
        // on the budget (the deadline is only checked every CHECK_INTERVAL_CHARS reads).
        assertTrue(elapsedMillis < 5_000L, "expected abort well under 5s, took " + elapsedMillis + "ms");
    }

    // @spec FILING-046
    @Test
    void exposesUnguardedViewsSoGroupsAreOrdinaryStrings() {
        String text = "record date March 3, 2024";
        BoundedRegexInput guarded = BoundedRegexInput.of(text, 2_000L);

        assertEquals(text.length(), guarded.length());
        assertEquals(text, guarded.toString());
        assertEquals("record", guarded.subSequence(0, 6).toString());
        assertEquals('r', guarded.charAt(0));
    }
}
