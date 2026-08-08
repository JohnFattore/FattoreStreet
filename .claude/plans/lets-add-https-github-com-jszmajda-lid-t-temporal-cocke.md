# Adopt Linked-Intent Development (LID), scoped pilot on corporate actions

## Context

This repo has a delivery workflow but no durable design record.

Spec Kit (`.specify/`, `specs/NNN-*/`) governs how a change gets built: spec, plan, tasks,
implement, gated by `.specify/memory/constitution.md`. It works, but its output is *per-change and
disposable*. `specs/002-retire-admin-routes/spec.md` defines `FR-001` through `FR-017`, and nothing
in `springboot/`, `django/`, or `react-app/` references any of them. Once the PR merges, the
requirements stop being consulted. Traceability today is one-directional and document-only.

The result is that the most intent-dense code in the repo has no machine-checkable statement of
what it is supposed to do. `springboot/.../corporateaction/` decides when a 4:1 split happened,
which SEC filing establishes an ex-date, and how far back to re-adjust prices. Those decisions live
in code, in `docs/equity-corporate-action-process.md` (prose, hand-maintained), and in the author's
head. A wrong answer silently corrupts every adjusted price downstream, and no test failure names
the intent that was violated.

[LID](https://github.com/jszmajda/lid) (MIT, Jess Szmajda) closes exactly that gap. It maintains
component-level design docs plus EARS requirements with stable semantic IDs, and has tests cite
those IDs via `@spec` annotations, so an audit can mechanically prove code still implements the
stated intent. Drift surfaces as a finding rather than as a surprise in production.

**Intended outcome:** LID installed as committed project tooling, piloted on the corporate-action
slice only, with an explicit written division of labor against Spec Kit so the two systems never
compete to be the requirements. Whether LID expands past the pilot is a decision made *after* the
pilot, not now.

## Division of labor (decided)

| | Spec Kit | LID |
|---|---|---|
| Owns | a **change** | a **component** |
| Lives in | `specs/NNN-slug/` | `docs/intent/<segment>/` |
| Lifetime | until merge | as long as the component exists |
| IDs | `FR-001`, `SC-001` (markdown only) | `PRICE-ADJ-001` (cited by tests) |
| Gate | constitution check | coherence audit |

Both stay. Spec Kit remains the front door for "I want to build X." LID answers "what is this
component supposed to do, and does it still do it?" A feature touching a LID-mapped segment updates
that segment's EARS specs as part of the work: design first, then tests, then code.

## Step-by-step walkthrough

### Step 1 (DONE) Register the marketplace and enable the plugins

Added to `.claude/settings.json`, alongside the existing `plansDirectory`, `statusLine`, `env`, and
`permissions` keys (all preserved):

```json
"extraKnownMarketplaces": {
  "jszmajda-lid": {
    "source": { "source": "github", "repo": "jszmajda/lid", "ref": "v1.3.0" }
  }
},
"enabledPlugins": {
  "linked-intent-dev@jszmajda-lid": true,
  "arrow-maintenance@jszmajda-lid": true
}
```

Pinned to tag `v1.3.0`, the latest, so an upstream push cannot change the workflow mid-pilot.
`lid-experimental@jszmajda-lid` (v0.2.0) is deliberately omitted: its `differential-audit` only has
something to audit once EARS specs and `@spec` annotations exist, which is Step 4.

**Requires a Claude Code restart to take effect.** After restarting, confirm `/plugin` lists both as
enabled and that `/linked-intent-dev` and `/arrow-maintenance:map-codebase` resolve.

> These plugins live in repo config, so the `claude-code-review.yml` GitHub Action environment sees
> them too. That job is advisory and not a merge gate, so this is safe, but expect its reviews to
> start referencing LID once `docs/intent/` exists.

### Step 2 Bootstrap LID in Scoped mode

Run `/linked-intent-dev`. Answer:

- **Scoped**, not Full. This is a pilot, not a monorepo commitment.
- Scope globs: `springboot/src/main/java/com/fattorestreet/sec_api/corporateaction/**` and
  `.../marketdata/**`.
- Claude-Code-only. Do **not** create `AGENTS.md`, do **not** symlink `CLAUDE.md` to `AGENTS.md`.
  This repo has one agent tool and one canonical `CLAUDE.md`; a second root instruction file to keep
  in sync is cost with no benefit today. Revisit only if another tool enters.

This wants to write `docs/high-level-design.md`. **Do not let it overwrite anything.**
`docs/ARCHITECTURE.md` already exists and is the de-facto HLD (Mermaid diagram, service
decomposition). Either point LID at `docs/ARCHITECTURE.md` as the HLD, or have it generate a thin
`docs/high-level-design.md` that links to `ARCHITECTURE.md` rather than restating it. Two documents
describing the same architecture is precisely the failure mode this plan exists to avoid.

### Step 3 Map the pilot segment

Run `/arrow-maintenance:map-codebase`, scoped to the same two packages. Six phases, each pausing for
review:

1. **Sweep.** Reads all 25 main classes and 25 test classes (7.7k main LOC, 7.0k test LOC).
2. **Lens selection.** Pick from 3 to 5 proposed clusterings. Feed it the structure that already
   exists: equity vs. ETF is the real seam (`EquityCorporateActionService` /
   `EtfCorporateActionService` and their disjoint `support/` helpers), with price adjustment and
   validation as separate concerns.
3. **Granularity.** Choose **medium (6 to 8 segments)**. Coarse collapses the equity/ETF distinction
   that matters; fine produces a segment per helper class and turns maintenance into busywork.
4. **Reconciliation.** Approve or redraw the boundaries. Expected shape: `equity-dividends`,
   `equity-splits`, `etf-distributions`, `price-adjustment`, `filing-discovery`, `price-validation`,
   `hist-ingest`.
5. **Artifact generation.** Skeleton LLDs and EARS specs per segment.
6. **Terminal verification.** LID offers to update `CLAUDE.md`; see Step 5, already done.

**Feed it `docs/equity-corporate-action-process.md` explicitly.** That 21K doc already contains the
equity pipeline's design intent and a file-to-role table. It should seed the equity LLDs, not be
silently duplicated by them. Once the LLDs exist, reduce that file to a pointer or retire it. Decide
at the end of the pilot, not during.

Expect this step to be token-expensive and to span more than one session. That is inherent to
`map-codebase`, not a sign something is wrong.

### Step 4 Flesh out EARS specs and annotate tests

The generated specs are skeletons. Convert observed behavior into EARS statements ("WHEN <trigger>,
THE SYSTEM SHALL <behavior>") with stable IDs, using the status markers LID defines: `[x]` works
today, `[ ]` specified but broken or partial, `[D]` deliberate non-want.

The `[ ]` and `[D]` markers are the highest-value output of this whole exercise. Known intent gaps
in this area, such as SEC detection scoped to `FAT1000` rather than the full ~24k IEX symbol set,
the rolling one-seventh-per-night refresh, and the >25% overnight-move trigger, are currently
recorded only in `CLAUDE.md` prose. As EARS entries they become checkable claims.

Then annotate tests. The 1:1 main-to-test class mapping makes this mechanical:

```java
// @spec PRICE-ADJ-003
@Test
void appliesSplitRatioToAllPriorRows() { ... }
```

**CI is safe for this.** `config/checkstyle/checkstyle.xml` disables every `Javadoc*` check ("no
Javadoc culture in this tree"), and Spotless is configured with no formatter and never reflows, so
comment-borne annotations pass `mvn verify` untouched. Annotate the highest-value tests first
(`PriceAdjustmentServiceTest`, `EquitySplitDetectorTest`, `EquityDividendFactParserTest`,
`CorporateActionValidationCanaryTest`) rather than all 25 at once.

### Step 5 (DONE) Reconcile the repo's own rules

Two existing rules would have fought LID:

- **`.claude/rules/auto-update-docs.md`** said *"Do NOT create new doc files unless the user
  explicitly asks."* Now carries an explicit exception for `docs/intent/` and `docs/arrows/`, a new
  change-type table row pointing LID-scope changes at their segment's LLD and specs, and a "LID
  scope is different" section stating that inside the scope docs are upstream of code and the "Skip
  When" list does not apply.
- **`CLAUDE.md`** now has a "Design Workflows: Spec Kit and LID" section carrying the division-of-
  labor table above, the two scope globs, the downstream-only rule, and the key commands. It states
  plainly that LID is scoped and that work outside those two packages must not use it.

### Step 6 Prove the loop closes

Run `/arrow-maintenance` and confirm it reports coherence across the mapped segments. Then
deliberately break one: change a behavior in `PriceAdjustmentService` without touching its spec,
re-run the audit, confirm it flags the drift, then revert. An audit that cannot detect a real break
is decoration.

### Step 7 Ship it

Per `.claude/plans/README.md`, this plan file is committed with the work. Two PRs:

1. Plugin config plus rules and `CLAUDE.md` reconciliation (Steps 1 and 5, ready now)
2. The generated `docs/intent/` and `docs/arrows/` tree plus test annotations (Steps 2 through 4)

## Files created and modified

**Modified (Steps 1 and 5, done)**
- `.claude/settings.json`, added `extraKnownMarketplaces` and `enabledPlugins`
- `CLAUDE.md`, new "Design Workflows: Spec Kit and LID" section
- `.claude/rules/auto-update-docs.md`, LID carve-out and scope override

**Still to modify (Steps 2 through 4)**
- `springboot/src/test/java/.../corporateaction/*Test.java`, `@spec` annotations, incremental
- `docs/equity-corporate-action-process.md`, reduce to pointer or retire, decide post-pilot

**Created by LID**
- `docs/high-level-design.md`, thin, linking to `docs/ARCHITECTURE.md`
- `docs/intent/<segment>/<segment>.md`, skeleton LLD per segment
- `docs/intent/<segment>/<segment>-specs.md`, EARS specs with stable IDs
- `docs/arrows/index.yaml`, segment taxonomy and status
- `docs/arrows/<segment>.md`, arrow docs, `status: MAPPED`

**Untouched:** `.specify/`, `specs/001-*`, `specs/002-*`, all `speckit-*` skills. No `AGENTS.md`.

## Verification

1. `/plugin` lists `linked-intent-dev` and `arrow-maintenance` as enabled; both commands resolve.
2. `docs/arrows/index.yaml` parses and every segment has an LLD and a specs file.
3. Every EARS ID is unique and every `[x]` claim maps to at least one annotated test.
4. `cd springboot && mvn clean verify` passes. Annotations are comment-only, Javadoc checks are off,
   Spotless does not reflow, and the 0.76 line-coverage floor is unaffected.
5. `/arrow-maintenance` reports coherence; the deliberate-break test from Step 6 produces a finding,
   then revert it.
6. `git status` shows no changes under `.specify/` or `specs/`, confirming the two systems stayed
   separate.

## Decide after the pilot, not now

- Whether to expand LID to `django/portfolio/` and `springboot/fundamentals/`, or stop here.
- Whether `lid-experimental`'s `differential-audit` earns its keep.
- Whether the coherence audit becomes a CI job (advisory, alongside `claude-code-review.yml`) or
  stays a manual command.
- Whether `docs/equity-corporate-action-process.md` survives as a document or becomes a pointer.

## Risk to watch

The pilot fails if `docs/intent/` becomes a second stale description of the same code. The
mitigation is Step 5: the rules must make spec updates a required part of changing the mapped code,
not an optional courtesy. If after one real feature the specs have gone stale anyway, that is the
signal to stop, and it is a cheap answer to have bought.
