# Finish the REDoS guard: route the last 5 flagged patterns through BoundedRegexInput

## Context

PR #138 ("Bound filing-text regex runtime with a match timeout") added
`corporateaction/support/BoundedRegexInput`, a `CharSequence` that aborts a regex match once it
exceeds a wall-clock budget, and rewrote SpotBugs `exclude.xml` block 8 from *"REVISIT - THIS IS A
REAL RISK, ACCEPTED FOR NOW"* to *"MITIGATED - no longer accepted risk"*.

**The mitigation is real but incomplete, and the PR merged anyway** (as `64e28008`, with the review
verdict at FAIL). The suppression is package-wide:

```xml
<Match>
    <Package name="~com\.fattorestreet\.sec_api\.corporateaction(\..*)?" />
    <Bug pattern="REDOS" />
</Match>
```

but the comment claims *"Every one of these patterns runs through CorporateActionFilingDateService
.extractDatedCandidates"*. Verified against the scanner by re-running SpotBugs on the PR head with
block 8 stripped out — 19 REDOS findings, not all in one place:

| Class | Findings | Match funnel | Guarded today |
|---|---|---|---|
| `CorporateActionFilingDateService` | 14 | `extractDatedCandidates` (line 634) | **yes** |
| `support/DividendDeclarationTupleExtractor` | 4 | `closestLabeledDate` (line 139) | no |
| `support/EtfDateExtractor` | 1 | `collectLabeledDateCandidates` (line 137) | no |

Both gaps are live production paths, not dead code: `closestLabeledDate` is reached from
`CorporateActionFilingDateService.extractDeclarationsFromFiling` and `EquityCorporateActionService`;
`collectLabeledDateCandidates` is reached from `EtfCorporateActionService:195`.

Runtime risk from the 5 is genuinely lower than from the 14 — `DividendDeclarationTupleExtractor`
matches inside a 1200-char window (`WINDOW_RADIUS = 600`), and `EtfDateExtractor`'s gap is
`[^\n\r\d]{0,50}` rather than `.{0,900}?` — so this is not urgent. What makes it worth fixing is
self-consistency: #138 also added a rule to `.claude/rules/springboot-java.md` saying *"Any regex run
over SEC filing text must match through `BoundedRegexInput`"*, and shipped in-package code violating
it, behind an `exclude.xml` comment asserting the risk class is closed.

**Outcome:** all 19 flagged patterns actually guarded, and the `exclude.xml` / README / rule text
describing what the code does rather than overclaiming.

## Base branch

Local `main` is **1 ahead / 4 behind** `origin/main`: it carries `60f885ad` ("Replace the claude.ai
review routine with a repo-tracked action") from unrelated in-flight work. Cut this branch from
`origin/main` so that commit isn't swept in.

```
git fetch origin && git switch -c fix/redos-guard-remaining-sites origin/main
```

## Changes

### 1. Share the budget constant — `support/BoundedRegexInput.java`

`REGEX_BUDGET_MILLIS = 2_000L` is currently `private static final` inside
`CorporateActionFilingDateService`. Three classes need it now. Promote it and add a convenience
factory:

```java
/** Default per-match budget ... (keep the existing rationale comment from the service). */
public static final long DEFAULT_BUDGET_MILLIS = 2_000L;

/** Wraps {@code text} with {@link #DEFAULT_BUDGET_MILLIS}. */
public static BoundedRegexInput of(CharSequence text) {
    return of(text, DEFAULT_BUDGET_MILLIS);
}
```

Delete `REGEX_BUDGET_MILLIS` from `CorporateActionFilingDateService` and switch line 634 to
`BoundedRegexInput.of(searchable)`. Leaving the private constant behind would fail the build:
`UnusedVariable` is ERROR-tier on `src/main` under `-Werror`.

### 2. Guard `closestLabeledDate` — `support/DividendDeclarationTupleExtractor.java`

One funnel carries all four flagged patterns (RECORD/PAYABLE/DECLARATION/EX_DATE), exactly mirroring
`extractDatedCandidates`. Wrap the input and catch, keeping matches already found:

```java
private LocalDate closestLabeledDate(String window, Pattern labelPattern, int anchorInWindow) {
    Matcher matcher = labelPattern.matcher(BoundedRegexInput.of(window, regexBudgetMillis));
    LocalDate best = null;
    int bestDistance = Integer.MAX_VALUE;
    try {
        while (matcher.find()) { /* unchanged body */ }
    } catch (BoundedRegexInput.RegexTimeoutException e) {
        // Keep the nearest date found so far. Same outcome as a window with no further
        // labeled dates, so one slow document never aborts the extraction.
        log.warn(...);
    }
    return best;
}
```

The class has no logger today; add the standard SLF4J field per `.claude/rules/springboot-java.md`.

**Test seam:** add `private final long regexBudgetMillis`, a public no-arg constructor delegating to
`BoundedRegexInput.DEFAULT_BUDGET_MILLIS`, and a package-private `(long)` constructor. Safe here —
the class is built with `new` at `CorporateActionFilingDateService:91` and in its own test, never via
`@InjectMocks`.

### 3. Guard `collectLabeledDateCandidates` — `support/EtfDateExtractor.java`

Same shape, wrapping `filingText` at line 137.

**Test seam must NOT be a constructor here.** `EtfDateExtractorTest` uses `@InjectMocks`, and Mockito
picks the widest constructor — a second one taking a `long` would break the existing tests. Thread
the budget through an overload instead; `collectLabeledDateCandidates` is called directly from
`extractEtfDateSignals` (line 64), so it's one hop:

```java
public EtfDateSignals extractEtfDateSignals(String filingText, LocalDate filingDate) {
    return extractEtfDateSignals(filingText, filingDate, BoundedRegexInput.DEFAULT_BUDGET_MILLIS);
}

// Visible for testing: budget override so a test can force the timeout path
// without a multi-second input.
EtfDateSignals extractEtfDateSignals(String filingText, LocalDate filingDate, long regexBudgetMillis) { ... }
```

### 4. Correct the overclaiming text

- **`springboot/config/spotbugs/exclude.xml`** block 8: replace *"Every one of these patterns runs
  through CorporateActionFilingDateService.extractDatedCandidates"* with the three funnels named
  explicitly (`extractDatedCandidates`, `DividendDeclarationTupleExtractor.closestLabeledDate`,
  `EtfDateExtractor.collectLabeledDateCandidates`) and the 14/4/1 split. Keep the block: FindSecBugs
  inspects pattern *construction*, so the finding can't be cleared.
- **`springboot/README.md`**, "Filing-text regexes": same correction.
- **`.claude/rules/springboot-java.md`**: narrow *"Any regex run over SEC filing text"* to the shape
  that actually matters — patterns pairing a lazy or bounded quantifier with a large alternation.
  As written the rule is still violated by `EtfAmountExtractor` and `EtfDateExtractor`'s
  sentence-level patterns, which FindSecBugs does **not** flag (simple greedy negated classes, no
  alternation) and which don't need the guard. A rule that overclaims is what produced this
  follow-up; state the real bar.

### 5. Tests

Both target classes already have tests to extend — no new files.

- **`DividendDeclarationTupleExtractorTest`** (plain JUnit, `new DividendDeclarationTupleExtractor()`):
  - normal input produces byte-identical results to before the guard (regression safety);
  - with a 1 ms budget and adversarial window text, `extract(...)` returns normally and no
    `RegexTimeoutException` escapes.
- **`EtfDateExtractorTest`** (`@ExtendWith(MockitoExtension.class)`, `@InjectMocks`): same pair via the
  package-private 3-arg overload.

Not doing: a service-level test for the pre-existing catch in `extractDatedCandidates`. Reaching it
needs `WebService` mocking plus a seam on the service's own budget, and the catch is structurally
identical to the two now under test. Calling that out rather than silently skipping it.

## Verification

```bash
cd springboot
./mvnw spotless:apply
./mvnw verify          # tests + JaCoCo floor (0.76) + SpotBugs/FindSecBugs + PMD + Error Prone
```

Then prove the claim this PR is making, the same way the gap was found:

1. Temporarily delete the `<Match>` block for `Bug pattern="REDOS"` from
   `config/spotbugs/exclude.xml`.
2. `./mvnw -DskipTests compile spotbugs:spotbugs`
3. Parse `target/spotbugsXml.xml` for `BugInstance[@type="REDOS"]` and confirm the count is still 19
   across the same three classes — the guard must not change which patterns exist, only how they run.
4. Restore the block.

Cross-check that behaviour is unchanged: every currently-passing test in
`DividendDeclarationTupleExtractorTest`, `EtfDateExtractorTest`, `EtfCorporateActionServiceTest` and
`EquityExDateAssignerTest` must still pass untouched. Guarding the input leaves the patterns
byte-identical, so any assertion change means something regressed.

## Out of scope

- `EtfAmountExtractor` and the sentence-level patterns in `EtfDateExtractor` /
  `CorporateActionFilingDateService`. Not REDOS-flagged, and their shapes don't warrant it; step 4
  adjusts the rule wording rather than expanding the guard to them.
- Enabling branch protection on `main` (it has none, `gh api .../branches/main/protection` → 404),
  which is why #138 merged with a failing review verdict in the first place.
