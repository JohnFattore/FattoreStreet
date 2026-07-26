package com.fattorestreet.sec_api.corporateaction.support;

/**
 * A {@link CharSequence} view of filing text that aborts a regex match once it exceeds a time
 * budget.
 *
 * <p>The corporate-action date extractors combine lazy bounded quantifiers ({@code .{0,900}?})
 * with a large month-name alternation. FindSecBugs flags that shape as REDOS: on adversarial or
 * merely unusual filing text the engine can backtrack long enough to stall the nightly ingest.
 *
 * <p>This guards the <em>input</em> rather than the patterns, which is the point. The obvious
 * alternatives both change what gets matched:
 * <ul>
 *   <li>Possessive quantifiers ({@code .{0,900}++}) are not a drop-in. The {@code ?} is lazy on
 *       purpose — it finds the <em>nearest</em> date after a keyword. Possessive matching consumes
 *       greedily and never gives characters back, which silently changes which date a filing
 *       resolves to.</li>
 *   <li>Truncating the input would bound runtime too, but could drop a legitimate date near the
 *       end of a long filing.</li>
 * </ul>
 * Guarding the input leaves every pattern byte-identical, so matching behaviour is unchanged for
 * all inputs that complete within budget, and only pathological ones are cut short.
 *
 * <p>Instances are stateful and single-use: the deadline starts at construction, so create one
 * per match operation. Not thread-safe.
 *
 * <p>{@link #subSequence} and {@link #toString} intentionally return unguarded views, so
 * {@code Matcher.group()} yields ordinary strings.
 */
public final class BoundedRegexInput implements CharSequence {

    /** Thrown when a regex match exceeds its budget. Callers treat this as "no match". */
    public static final class RegexTimeoutException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        RegexTimeoutException(String message) {
            super(message);
        }
    }

    /**
     * Default per-match wall-clock budget. Generous next to a normal match (low single-digit
     * milliseconds even on large filings) but small next to a nightly ingest, so a pathological
     * document costs a fraction of a second instead of stalling the run.
     */
    public static final long DEFAULT_BUDGET_MILLIS = 2_000L;

    /**
     * Characters read between deadline checks. Reading the clock on every {@code charAt} would
     * cost more than the backtracking it guards against; a few thousand characters is far below
     * the budget yet still bounds overshoot.
     */
    private static final int CHECK_INTERVAL_CHARS = 4096;

    private final CharSequence delegate;
    private final long deadlineNanos;
    private final long budgetMillis;
    private int charsUntilCheck = CHECK_INTERVAL_CHARS;

    private BoundedRegexInput(CharSequence delegate, long budgetMillis) {
        this.delegate = delegate;
        this.budgetMillis = budgetMillis;
        this.deadlineNanos = System.nanoTime() + budgetMillis * 1_000_000L;
    }

    /**
     * Wraps {@code text} so matching against it aborts after {@code budgetMillis}.
     *
     * @param text         the text to match against
     * @param budgetMillis wall-clock budget for the whole match operation
     * @return a guarded view of {@code text}
     */
    public static BoundedRegexInput of(CharSequence text, long budgetMillis) {
        return new BoundedRegexInput(text, budgetMillis);
    }

    /**
     * Wraps {@code text} with {@link #DEFAULT_BUDGET_MILLIS}. Preferred over the explicit-budget
     * overload; pass a budget only when a test needs to force the timeout path.
     *
     * @param text the text to match against
     * @return a guarded view of {@code text}
     */
    public static BoundedRegexInput of(CharSequence text) {
        return of(text, DEFAULT_BUDGET_MILLIS);
    }

    @Override
    public int length() {
        return delegate.length();
    }

    @Override
    public char charAt(int index) {
        if (--charsUntilCheck <= 0) {
            charsUntilCheck = CHECK_INTERVAL_CHARS;
            if (System.nanoTime() > deadlineNanos) {
                throw new RegexTimeoutException(
                        "Regex match exceeded " + budgetMillis + "ms over " + delegate.length() + " chars");
            }
        }
        return delegate.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return delegate.subSequence(start, end);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
