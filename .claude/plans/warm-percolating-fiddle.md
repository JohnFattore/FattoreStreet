# Grow the intent tree: overview + one branch per app

## Context

`intent/` has a root README (vision), `principles.md`, and one built-out branch (`market-data/`).
The other branches in the root table read "not yet written". Goal: give every app a branch README plus
its first leaves, so the tree covers the whole product. Decisions from the owner:

- A **new HLD doc**, distinct from the root README. The README stays the vision (who, problem,
  success, non-goals). The HLD says how the product is shaped and *why each major technology was
  chosen*.
- The intent skill's "no how" rule is too strict at the top. Loosen it so a high-level why may name
  the underlying tech.
- Restaurants and entertainment get **separate** branches.
- Add an **accounts** branch.
- Write **README plus leaves** for each branch, not just READMEs.

Split stays by purpose, not service (per `.claude/skills/intent/SKILL.md`).

## Approach

Follow the intent skill's **Grow** mode for each branch: read root + `design.md` + principles, read
the code that implements the area, draft from `.claude/skills/intent/references/node-template.md`,
mark everything not confirmed by the owner `(inferred)`, put contradictions in `## Open questions`,
run the validator. Follow the loosened altitude rules (tech allowed in branch READMEs with a why,
never in leaves; never file paths/endpoints/table names). Use `intent/market-data/` as the style
reference. No em dashes in prose.

### Skill change (`.claude/skills/intent/SKILL.md`), done first

Replace the blanket "No how" rule with altitude-based rules:

- **`design.md` and branch READMEs may name tech**: services, major frameworks, data stores, external
  sources and the scheduled-job model, each with the reason it was chosen and the failure that
  choice prevents (e.g. "prices load as scheduled one-shot jobs because nothing should bill
  between runs"). Tech in a why is a decision, not an implementation detail.
- **Leaves stay tech-free.** Requirements are still behavior that survives a refactor.
- **Banned everywhere:** file paths, class/table/column names, endpoints, env vars, tuning numbers.
  These go stale on refactor at any altitude.
- Add `design.md` to "The tree" diagram, and a short section on its shape.
- A tech change that alters a design decision is a **Sync** trigger for `design.md`.

The validator needs no change: `design.md` carries a `## Why` like any node.

### HLD (`intent/design.md`), new

Sections (owner can reshape after the draft):

- **Why**: the vision's promises (primary-source, free to use, auditable, hobby budget, one
  maintainer) force specific design choices; this doc records them.
- **The parts**: each product area as a box with its role, in plain terms, then the services that
  carry them: the web app, the main API (accounts, portfolio, advisor, blog, recommendations,
  feedback), the market-data service and its scheduled jobs, the shared database and cache, the
  reverse proxy, and local AI tooling. One line each on why it's separate.
- **How the parts depend on each other**: security universe -> raw prices + corporate actions ->
  adjusted prices -> indexes and fundamentals -> portfolio and security pages. Likely a mermaid
  diagram.
- **External sources**: SEC filings, exchange price history, FRED, Gemini, and each one's license
  status. Known non-free ones in use (yfinance, Finnhub, Yelp, the fund-provider holdings file)
  listed as open questions, not blessed.
- **Who can change what**: public read, signed-in user's own data, author-only content (blog from
  the repo, recommendations from admin), shared data only via scheduled jobs. Ties to *Least
  privilege* in principles.
- **Key decisions, each with its why**: two backends split by workload (user CRUD vs heavy batch
  ingest); scheduled one-shot jobs instead of always-on workers; jobs spaced so the per-process SEC
  rate limit holds across processes; heavy work scoped to one index; the repo as source of truth
  for blog content; merge-to-main deploys.
- **Open questions**: e.g. should the market-data service ever serve user data, or stay read-only
  and public? Should `docs/ARCHITECTURE.md` (stale: no Spring Boot, still called "Portfolio
  Manager") be retired in favour of `design.md`?

Everything drawn from code rather than the owner is marked `(inferred)`.

### Root overview (`intent/README.md`), light touch

- Link to `design.md` near the top ("how it's shaped: see design").
- Replace the "not yet written" Branches table with links to every branch below.
- Split "Restaurants, entertainment" into two rows; add Accounts.
- No other content changes.

### Market data (existing, one addition)

- New leaf `market-data/security-universe.md`: which securities exist, their tickers and filer
  identity, which are funds. Everything else depends on it. Link from `market-data/README.md`.
- Price validation is already covered by *Accuracy is measured, not assumed* in
  `adjusted-prices.md`, so nothing to add there.

### Finance branches

**`intent/fundamentals/`**
- `README.md`: why (the vision's "fundamentals you can trust", from the companies' own filings)
- `quarterly-financials.md`: quarterly statement figures, current within a defined window
- `derived-metrics.md`: trailing-twelve-month figures and ratios, traceable to the filings
- `filing-summaries.md`: annual-report summaries
- Open questions: summaries are frozen (generator retired): keep read-only, revive, or remove?
  The company page also shows a yfinance quarterly table to users, against *Only commercially free data*

**`intent/indexes/`**
- `README.md`: why (benchmarks for users, plus the scope that keeps heavy work within free-source limits)
- `market-cap.md`: free-float market cap per security, from filings and exchange prices
- `construction.md`: ranking, weighting and the daily rebuild of the Fattore indexes
- `benchmark-comparison.md`: comparing a Fattore index with the index it approximates
- Open questions: the comparison holdings are a fund provider's file (license unverified), and
  naming the official index sits close to the "not official index products" non-goal

**`intent/economic-indicators/`**
- `README.md`: why (macro context for long-term decisions)
- `series.md`: which series are shown and why, including year-over-year views
- Open questions: some series (e.g. VIX) may carry third-party copyright even when served through
  FRED; data is fetched live on each view with no story for staying current or failing gracefully

**`intent/portfolio/`**
- `README.md`: why (the vision's "track your own portfolio against that data")
- `holdings.md`: a user's accounts and holdings, with buy and sell dates
- `performance.md`: returns and comparison with benchmarks
- `watch-list.md`: securities a user follows but doesn't hold
- `security-page.md`: the per-security view (price, dividends, splits, quote)
- Open questions (major): most portfolio, security-page and watch-list numbers come from yfinance
  and Finnhub, which violates *Only commercially free data*. Is the target to move them onto the
  platform's own market data, or is a named temporary exception allowed? Also: a public price
  comparison page shows yfinance next to exchange prices (diagnostics belong in dev only)

**`intent/advisor/`**
- `README.md`: why (a passive-investing guide for the core audience)
- `persona.md`: passive outlook, informs but never recommends specific trades
- `conversation-history.md`: history kept per user, private to them
- Open questions: nothing currently enforces "not investment advice" beyond the persona; retention
  for stored conversations

### Non-finance branches

**`intent/accounts/`**
- `README.md`: why (portfolio, reviews, feedback need a person to belong to; one user's data must not leak to another)
- `sign-in.md`: registration, sign-in, session renewal
- `data-ownership.md`: per-user data is visible and editable only by its owner
- Open questions: preferences (dark mode) are browser-only and don't follow the user across devices; ownership is enforced per app with no shared rule

**`intent/blog/`**
- `README.md`: why (the author's public writing, part of "public workspace" in the root)
- `reading.md`: published-only, search, categories, tags
- `journal.md`: author's posts; the repo is the source of truth, merging publishes
- `llm-notebook.md`: Claude-written study topics, labelled as such
- Open questions: does the repo or the admin win when a post is edited in both; categories/tags exist but aren't browsable in the UI

**`intent/restaurants/`**
- `README.md`: why (the author's personal recommendations)
- `directory.md`: restaurants browsable by place
- `reviews.md`: a user's own reviews and map
- Open questions (important): restaurant data appears Yelp-sourced with no ingest code left, which conflicts with *Only commercially free data is stored or shown*; anyone can add restaurants, conflicting with *Least privilege*; recommendations endpoint is dead

**`intent/entertainment/`**
- `README.md`: why
- `recommendations.md`: the author's curated books, movies, shows, music, podcasts, games, websites; public read, author-only write
- Open questions: two competing pages (server-rendered list vs hard-coded React page); which is canonical

**`intent/feedback/`**
- `README.md`: why (one maintainer needs users to report problems)
- `tickets.md`: logged-in users can report a problem
- `changelog.md`: what changed, visible to users
- Open questions: any logged-in user can edit/delete changelog entries (violates *Least privilege*); no public changelog page; users can't see their own tickets

## Rules while writing

- Contradictions between code and principles (yfinance/Finnhub, Yelp, IWB, open writes) are
  recorded as **open questions only**. This pass changes no code and doesn't "resolve" them by
  loosening a principle.
- Leaf list above is the starting shape. If reading the code shows a leaf has two reasons to exist
  or is nearly empty, split or merge it and say so in the summary.
- Each branch README lists its children with links (like `market-data/README.md`).

## Order

0. Skill change, then `intent/design.md` (branches lean on it)
1. Accounts (other branches reference ownership)
2. Market data `security-universe.md`
3. Fundamentals, Indexes, Portfolio, Advisor, Economic indicators
4. Blog, Restaurants, Entertainment, Feedback
5. Root README branch table last, once all links exist

## Verification

- `python3 .claude/skills/intent/scripts/validate.py` passes after each branch (no missing READMEs,
  broken links or nodes without a why).
- `python3 .claude/skills/intent/scripts/validate.py --summary` at the end; report per-branch
  `(inferred)` and open-question counts as the backlog for Interview mode.
- How-leakage grep: `grep -rnE '\.py|\.tsx?|\.java|/api/' intent/` should return nothing anywhere.
  `grep -rnE 'Django|Spring|React|Postgres|Redis|Fargate' intent/ --include='*.md'` should hit only
  `design.md` and branch READMEs, never a leaf. Grep for em dashes too.

## Delivery

One branch off `main`, one PR containing the skill change, the new intent files and this plan file
(`.claude/plans/warm-percolating-fiddle.md`). No merge.
