---
parent: high-level-design
prefix: PRICE-VAL
---

# Price Validation

## Context and Design Philosophy

This segment answers "is the adjusted price series right" and, when it is not, "which action on which
date is wrong." It is the only segment that writes nothing.

**The pipeline has no internal way to know it is wrong.** Every other segment derives; a derivation that
is confidently wrong produces a plausible series, renders fine in every chart, and fails no test. So the
only available check is an external reference, and the only free one is not licensed for commercial reuse.
That constraint shapes the whole segment: the reference may be read, compared in memory, and reduced to
derived statistics that reach an operator's inbox, and it may never touch a table, an API response, or the
UI.

**The central idea is that a ratio series localizes errors.** Dividing the stored adjusted close by the
reference adjusted close, date by date, produces a step function rather than noise. Each step marks the
exact date of a missing, extra, or misdated corporate action, and the step's magnitude is the implied size
of that action. This converts a useless finding ("this ticker's prices disagree") into an actionable one
("something worth 4.0x happened on 2020-08-31 and we do not have it").

**Read-only is a licensing property, so it is tested rather than trusted.** The runner's read-only nature
is asserted in its test instead of left to convention, because a future edit that added a write here would
violate the data-licensing rule silently.

## Two Validators at Different Altitudes

| | `AdjustedPriceValidationService` | `CorporateActionValidationService` |
|---|---|---|
| Compares | the adjusted close series | individual split and dividend events |
| Answers | is the end product users see correct | which events are missing, extra, or off |
| Used by | the weekly report and opt-in diagnostics | opt-in diagnostics only |

The price validator is the one that matters for the weekly report, because it validates the artifact
rather than the inputs. An event diff can look clean while the series is still wrong (an event present at
the right date with the wrong scale), and it can look alarming while the series is fine (a reference
dividend we deliberately classify differently).

Both reach the reference through Django's portfolio endpoint rather than fetching it directly, which keeps
the licensing boundary at one process edge.

## The Ratio Method

For each date present in both series, the ratio is `storedAdjustedClose / referenceAdjustedClose`. The
ratio series is then **normalized at the latest common date**.

Normalizing at the newest date means a constant level offset between the two series reads as zero
deviation everywhere, and what remains is exactly the effect of event differences. This is deliberate: the
question is not whether the two series agree in absolute terms (they use different bases and different
venues) but whether they agree about what happened.

Two consequences follow, and both are load-bearing:

- **A uniform scale difference is invisible by construction.** If both series were off by one constant
  factor across the whole history, the normalized deviation would be zero. That is the intended trade,
  since a constant factor is not an error in the adjustment logic.
- **The anchor is a single point of failure.** Every deviation is measured against the newest date's
  ratio. If that one date is corrupted (a bad recent print on either side), the entire history reads as
  out of tolerance, and the report blames the ticker rather than its anchor.

### Measures and thresholds

| Measure | Threshold | Meaning |
|---|---|---|
| Level deviation, `abs(ratio / anchor - 1)` per date | 0.005 | Dates counted as out of tolerance |
| Day-over-day ratio step | 0.01 | Reported as a break: a localized bad event |
| Break-to-action attribution window | 7 days | A stored action this close to a break is named as its likely cause |

A break carries its date, its signed step size, and the nearest stored action. Naming the nearest action
is what makes the report actionable: a break with a stored split next to it is a scale problem, and a break
with nothing next to it is a missing event.

Per ticker at most 20 breaks are kept; a batch keeps at most 10 worst tickers and 20 breaks overall. The
caps exist because the report is an email, not a dataset.

### Per-ticker statuses

| Status | Condition |
|---|---|
| `no_adjusted_prices` | Nothing stored for the ticker in the window |
| `insufficient_overlap` | Fewer than two stored rows, or fewer than two dates common to both series |
| `no_reference_data` | The reference returned nothing |
| `ok` | Compared, with statistics reported |

## Event Diffing

The corporate-action validator pairs stored events against reference events and reports the differences.
Pairing windows are deliberately loose, because their job is to decide *which* stored event corresponds to
*which* reference event, not to judge correctness:

| Comparison | Tolerance |
|---|---|
| Dividend pairing by date | 60 days |
| Split pairing by date | 7 days |
| Dividend amount agreement | 2% |
| Split ratio agreement | 1% |

A 60-day dividend window pairs a stored ex-date with a reference ex-date even when our assignment was a
month off, which is the point: the pair is then reported with its date difference, whereas a tight window
would report the same situation as one missing and one extra event and lose the correspondence.

Reference splits are stated as a share multiplier (4 for a 4:1 split) and are normalized to the stored
ratio convention before comparison.

## The Weekly Task

Active only in `validate-prices` run mode. It is the scheduled replacement for the retired
`GET /admin/validate-adjusted-prices` endpoint and the only run mode that is purely diagnostic.

Scope is one index (`app.validate-prices.index-code`, default `FAT1000`), ordered by index weight
descending, because the price universe is the full IEX symbol set and most of it has no reference
counterpart. `app.validate-prices.max-tickers` caps the run, and because the ordering is by weight the cap
keeps the most consequential names rather than an arbitrary slice.

Exit codes are the task's signal to EventBridge:

| Condition | Exit |
|---|---|
| Completed, including runs where individual tickers were skipped | 0 |
| No SNS topic configured (report logged instead) | 0 |
| The configured index resolves to zero members | 1 |
| The configured minimum date will not parse | 1 |
| The report could not be delivered | 1 |

**An empty scope is a failure, not a healthy run.** A report reading "0 tickers checked, all healthy" is
worse than a failed task, because it is indistinguishable from a genuinely clean week. The same reasoning
makes an undeliverable report fatal: a report nobody receives is indistinguishable from a run that never
happened.

Individual ticker failures are not fatal. Over a thousand names, a partial report is far more useful than
none.

## Report Delivery

The publisher is instantiated only in `validate-prices` mode, so the default server deployment never
builds an SNS client or looks for AWS credentials.

SNS email carries no attachments and caps a message at 256 KB, so the body is plain text and bounded
twice: the validation service already caps its worst-ticker and break samples, and rendering additionally
hard-truncates anything still over 250 KB, leaving headroom for the truncation notice itself. **Truncation
is always announced in the body**, because a silently shortened report reads as a complete one. Subjects
are capped at 100 ASCII characters, which is what SNS accepts.

Every value rendered is a derived comparison statistic. No raw reference series is carried into the report.

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| Validation strategy | Compare against an external reference | Internal consistency checks only | A confidently wrong derivation is internally consistent. Only an outside opinion can catch it. |
| Reference source | yfinance, read through Django, never persisted | A licensed feed; no reference at all | It is the only free adjusted-close reference available. Routing it through Django and persisting nothing keeps the licensing boundary at one process edge. |
| Read-only guarantee | Asserted in a test | Left to code review | It is a licensing property, not a style preference. A future write added here would otherwise violate the rule silently. |
| Comparison metric | Ratio series normalized at the latest common date | Absolute price differences; percentage error per date | The normalized ratio is a step function whose steps localize individual bad events. Absolute differences would report every date as wrong and localize nothing. |
| Normalization anchor | Latest common date | Earliest common date; mean ratio | Anchoring at the newest date makes recent history the reference point, which is where a new error appears first. It also makes a constant level offset read as zero. |
| Two validators | Series-level and event-level, kept separate | One combined validator | They fail independently: a clean event diff can accompany a wrong series, and an alarming diff can accompany a correct one. Collapsing them would hide both cases. |
| Weekly report scope | One index, ordered by weight | Every ticker with prices; a random sample | Most of the price universe has no reference counterpart. Weight ordering means a cap keeps the names that matter. |
| Dividend pairing window | 60 days | A tight window matching the tolerance we want | The window's job is correspondence, not judgment. A tight window turns "our date was a month off" into one missing plus one extra event and loses the pair. |
| Empty scope | Exit non-zero | Report zero tickers as healthy | A clean report over an empty scope is indistinguishable from a genuinely clean week, which is the one failure mode a monitoring task must not have. |
| Undeliverable report | Exit non-zero | Log and succeed | A report nobody receives is indistinguishable from a run that never happened. |
| Per-ticker failures | Count, skip, continue, exit zero | Fail the run | Over a thousand names a partial report is much better than none, and the skip count is reported. |
| Report format | Plain text email, double-bounded, truncation announced | Attachment; a stored dataset; a dashboard | SNS email carries no attachments and caps at 256 KB. Announcing truncation is what keeps a shortened report from reading as a complete one. |
| Publisher instantiation | Only in `validate-prices` run mode | Always available | Keeps AWS SNS credentials and client construction out of the server deployment entirely. |

## Open Questions & Future Decisions

### Resolved

1. ✅ Whether an empty validation scope is a healthy run: no, it exits non-zero.
2. ✅ Whether an undeliverable report should fail the task: yes, for the same reason.
3. ✅ Whether the reference may be persisted for trend analysis: no, the licensing rule forbids it.

### Deferred

1. **Nothing stores the weekly results.** Each report is emailed and gone. The project-level success
   metric is the share of in-scope tickers within tolerance *across consecutive weeks*, and its
   falsification signal is that share rising over time, neither of which is mechanically observable from
   an inbox. Storing the derived statistics (not the reference series, which the licensing rule forbids)
   would make the metric real. This is the largest gap in the segment.
2. **A run where every ticker returns `no_reference_data` exits zero.** The empty-index guard catches an
   empty scope, but if Django is unreachable the scope is full, every ticker is skipped, and the report
   says zero checked and zero out of tolerance. That is the same silent-healthy failure the empty-index
   guard exists to prevent, one level down.
3. The normalization anchor is a single date, so one bad recent print on either side makes an entire
   ticker's history read as out of tolerance. A more robust anchor (a median over the last several common
   dates) would cost nothing and is not implemented.
4. Break attribution names the nearest stored action within 7 days but does not say whether the break is
   consistent with that action's size. A break of 4.0x next to a stored 4:1 split is a scale bug; the same
   break next to a stored dividend is a different bug; the report does not distinguish them.
5. The canary ticker lists used by the accuracy-pass workflows live in a test class
   (`CorporateActionValidationCanaryTest`), so production-adjacent configuration is stored in test scope
   and is not reachable from any non-test code.
6. Thresholds (0.005 level, 0.01 break, 7-day attribution) have no recorded derivation. Unlike the tuning
   constants elsewhere in the pipeline, these directly determine what the weekly report says is broken, so
   a poorly chosen one produces either alert fatigue or silence.
7. There is no per-ticker history, so a ticker that is persistently and identically wrong is reported every
   week with the same numbers and cannot be distinguished from a new regression.

## References

- [High-Level Design](../../high-level-design.md): the success metrics and falsification signals this
  segment is supposed to produce, and the licensing constraint that bounds it.
- [`price-adjustment`](../price-adjustment/price-adjustment-design.md): produces the adjusted series this
  segment measures.
- [`equity-actions`](../equity-actions/equity-actions-design.md) and
  [`etf-distributions`](../etf-distributions/etf-distributions-design.md): produce the events the event
  diff compares.
- `.claude/rules/data-licensing-commercial-free.md`: the constraint that makes this segment read-only.
- `index/`: maintains the index membership and weights that scope and order the weekly run.
