---

description: "Task list for the LLM Notebook blog publish path"
---

# Tasks: LLM Notebook Blog Pipeline

**Input**: Design documents from `/specs/001-llm-notebook-blog/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/sync-blog-posts.md](./contracts/sync-blog-posts.md), [quickstart.md](./quickstart.md)

**Tests**: Included, and not optional here — `.claude/rules/auto-update-tests.md` is an always-on project rule requiring test updates alongside changes to application logic. The tests are also the only thing standing between a deploy and damage to the eight posts already live.

**Organization**: Grouped by user story. US1 is deliverable on its own and is the MVP.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1, US2, US3 per [spec.md](./spec.md)
- Paths are repo-relative from `/home/spike/Documents/GitHub/FattoreStreet`

## Path Conventions

Django web service. Application code in `django/`, tests in `django/tests/`, deploy scripts in `deploy/`. Commands run from `django/` unless stated otherwise.

---

## Phase 1: Setup

**Purpose**: Working environment and a recorded baseline of production to verify against later.

- [X] T001 Install dependencies: run `uv sync` in `django/`
- [X] T002 [P] Record the production baseline to `/tmp/claude-1000/-home-spike-Documents-GitHub-FattoreStreet/7a67a21b-a709-4ca3-b10c-9f3fc789716b/scratchpad/blog-baseline.json` by fetching `https://fattorestreet.com/django/blog/api/posts/?page_size=50` — this is the 8-post, no-duplicates state that SC-002 is measured against after the first production run

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Get PR #133's command onto this branch. Every user story builds on it, and nothing below can start until it is here.

- [X] T003 Bring the management command onto this branch from PR #133: `git checkout origin/claude/fattorestreet-lessons-blog-hg7cbc -- django/blog/management/ django/tests/test_blog.py` (brings `django/blog/management/__init__.py`, `django/blog/management/commands/__init__.py`, `django/blog/management/commands/sync_blog_posts.py`, and the existing 8 command tests)
- [X] T004 Confirm the command is discoverable: `uv run python manage.py help sync_blog_posts` from `django/` prints its usage without an `ImportError` on `settings.BLOG_POSTS_DIR`
- [X] T005 Remove the `BLOG_POSTS_DIR` setting from `django/mysite/settings.py` — it exists only to point at a directory outside the image, and per [research.md](./research.md) R-002 the two post directories are now resolved relative to the `blog` app package instead

**Checkpoint**: The command exists on this branch and runs. It still reads one directory and derives everything the old way — that is what Phase 3 fixes.

---

## Phase 3: User Story 1 — A reader finds the LLM Notebook on the site (Priority: P1) 🎯 MVP

**Goal**: The twenty Markdown files become correct `Post` rows — right slug, right category, right date, right excerpt — with no damage to the eight posts already live.

**Independent test**: Run `sync_blog_posts` against a local database and query the API. Thirteen posts under `llm-notebook`, twenty-one total, none duplicated. Delivers value with no deploy automation at all — the command can be run by hand.

### Implementation for User Story 1

Tasks T006–T011 all edit `django/blog/management/commands/sync_blog_posts.py` and are therefore strictly sequential — no `[P]`.

- [X] T006 [US1] Replace single-directory discovery in `django/blog/management/commands/sync_blog_posts.py` with the two built-in post types — `journal/` and `learning-topics/`, resolved from the `blog` package's own location (`Path(__file__).resolve().parents[2]`) so the same code works in the container, a dev checkout, and CI. Keep `--path` as a single-directory override and add `--category NAME` to pair with it, per [contracts/sync-blog-posts.md](./contracts/sync-blog-posts.md)
- [X] T007 [US1] Update slug derivation in `django/blog/management/commands/sync_blog_posts.py`: filename stem, `_`→`-`, slugified, with a leading `<digits>_` stripped for learning topics so the issue number stays out of the public URL. Front matter `slug:` continues to win. See [research.md](./research.md) R-003
- [X] T008 [US1] Fix `derive_excerpt` in `django/blog/management/commands/sync_blog_posts.py` to skip the header block — heading lines, blank lines, and whole-line italic metadata (`_…_`) — before taking the first paragraph. Without this every post's excerpt is its version stamp (R-005)
- [X] T009 [US1] Add version-stamp date parsing to `django/blog/management/commands/sync_blog_posts.py`: read the trailing ` — YYYY-MM-DD` from the stamp line below the title and use it as the default `published_at` (midnight, default timezone), reusing the existing `parse_published_at` for timezone handling. Precedence: front matter `published_at:` → version stamp → `--publish`/now → null. Never clear an existing value. This is what makes the import idempotent (R-004)
- [X] T010 [US1] Add the directory-implied category to `django/blog/management/commands/sync_blog_posts.py`: posts from `learning-topics/` get `LLM Notebook` via the existing `get_or_create_terms`, **added** to the post's categories rather than replacing them, so a category set by hand in the admin survives the next deploy (R-007)
- [X] T011 [US1] Add cross-directory duplicate-slug detection to `django/blog/management/commands/sync_blog_posts.py`, raising `CommandError` before any file is written
- [X] T012 [P] [US1] Add `slug: index-funds-rock` front matter to `django/blog/journal/INDEX_FUNDS_101.md` so it updates the live post instead of creating a duplicate. If the live title "Index Funds Rock!" should be kept, add `title: Index Funds Rock!` here too — otherwise the file's "Index Funds 101" replaces it
- [X] T013 [P] [US1] Add `slug: json-web-tokens` front matter to `django/blog/journal/JSON_WEB_TOKEN.md` so it updates the live post instead of creating a duplicate

### Tests for User Story 1

All of these live in `django/tests/test_blog.py`, so they are sequential with each other but independent of T006–T013 once those land.

- [X] T014 [US1] Update the existing PR #133 tests in `django/tests/test_blog.py` that assume a single `docs/blog-posts/` directory and `settings.BLOG_POSTS_DIR`
- [X] T015 [US1] Add tests in `django/tests/test_blog.py` for slug derivation: a journal filename, a learning-topic filename with its issue number stripped, and a front matter `slug:` override winning over both
- [X] T016 [US1] Add a test in `django/tests/test_blog.py` that a learning-topic post is filed under `LLM Notebook` and that a category added afterwards by hand survives a re-import (FR-002, R-007)
- [X] T017 [US1] Add a test in `django/tests/test_blog.py` that `published_at` comes from the version stamp, and that a post with no stamp and no `--publish` stays a draft (FR-009)
- [X] T018 [US1] Add a test in `django/tests/test_blog.py` that the excerpt is the first real paragraph and never the version stamp (R-005)
- [X] T019 [US1] Add a test in `django/tests/test_blog.py` that a published post with no file behind it — the live `react` case — is untouched by an import, and that re-importing an already-published post neither unpublishes it nor moves its date (FR-011)
- [X] T020 [US1] Add a test in `django/tests/test_blog.py` that two files resolving to the same slug raise before anything is written (FR-010)
- [X] T021 [US1] Add an idempotency test in `django/tests/test_blog.py`: import twice, assert the second run reports zero creations and that no row's `updated_at` semantics or `published_at` changed (FR-009, SC-004)
- [X] T022 [US1] Run `uv run python manage.py test tests.test_blog` from `django/` and get it green

**Checkpoint**: US1 is complete and independently demonstrable. Walk [quickstart.md](./quickstart.md) steps 1–4: twenty files map to the expected slugs, categories, dates, and excerpts, and a second run is a no-op.

---

## Phase 4: User Story 2 — Merging to main publishes the posts (Priority: P2)

**Goal**: The command runs on every merge to main with no manual step, and its failure fails the deploy.

**Independent test**: Merge a branch that edits one post file; the change is live once the deploy reports success, with nobody logging into a server.

**Depends on**: Phase 3. There is no point deploying a command that derives the wrong slugs.

### Implementation for User Story 2

- [X] T023 [US2] Append `!blog/journal/*.md` and `!blog/learning-topics/*.md` to `django/.dockerignore` after the existing `*.md` line, so the posts survive into the image while `README.md`, `CLAUDE.md`, and other stray Markdown stay out (R-001). **This is the one change that fails silently** — the build succeeds and the container simply has no posts
- [ ] T024 [US2] **BLOCKED — Docker daemon not running locally.** Verify the fix inside a built image, not by reading the file: `docker build -t fs-django-check django/` then `docker run --rm --entrypoint sh fs-django-check -c 'ls blog/journal/*.md blog/learning-topics/*.md | wc -l'` must print `20`.

  **R-001 was wrong** and the plan/research are corrected: `.dockerignore` patterns match the full relative path with Go `filepath.Match` semantics, where `*` does not cross `/`. The bare `*.md` therefore only ever matched root-level `README.md`/`CLAUDE.md` — the posts were never excluded, and this was not the blocker it was billed as. The negation lines were kept anyway: they are correct under either reading and document the dependency so nobody later broadens the pattern to `**/*.md`. Still worth running this check once Docker is available, since it costs nothing and the failure mode is silent.
- [X] T025 [US2] Add `docker compose run --rm django python manage.py sync_blog_posts` to `deploy/deploy.sh` immediately after the existing `migrate` line and before the health check, with a comment explaining that the Markdown files in the image are the source of truth. `--publish` is deliberately omitted (R-006). `set -eu` already makes a non-zero exit fail the deploy, the SSM step already prints stdout and stderr, and the workflow already gates on the final status — no error handling to add (FR-012)

**Checkpoint**: A merge to main publishes. Quickstart step 5 passes.

---

## Phase 5: User Story 3 — Tomorrow's learning topic lands in the repo by itself (Priority: P3)

**Goal**: Each new daily learning topic arrives as a reviewable PR carrying its post file.

**Already delivered** — the CronJob routine opens the PR and the `blog-editor` skill records the format (FR-015, FR-016, FR-017). These tasks verify that rather than building it.

- [X] T026 [P] [US3] Verify `.claude/skills/blog-editor/SKILL.md` states the learning-topic filename convention (`<issue-number>_<TITLE>.md`) and version-stamp format that T007 and T009 parse, so the routine and the importer cannot drift apart. Correct the skill if they disagree — the skill is the format's owner
- [X] T027 [P] [US3] Confirm no code change is needed for FR-016 (nothing publishes until a human merges) and note in `specs/001-llm-notebook-blog/tasks.md` that US3 required verification only

**Checkpoint**: The daily loop is closed — issue, PR, merge, deploy, live.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T028 [P] Update `django/README.md` with the `sync_blog_posts` command: both directories, the flags, the front matter keys, the version-stamp date behaviour, and the LLM Notebook rule (`.claude/rules/auto-update-docs.md`)
- [X] T029 [P] Update `CLAUDE.md` — document the two post directories under the Django Apps section and confirm no `BLOG_POSTS_DIR` env var is listed, since T005 removed it
- [X] T030 Run the full Django suite: `uv run python manage.py test` from `django/`
- [X] T031 [P] Run `pre-commit run detect-secrets --all-files` from the repo root — thirteen new posts discuss auth and infrastructure, and the JWT topic names `SECRET_KEY` as an identifier. Per `.claude/rules/secrets-check.md`, a finding is pragma'd at the source only when provably a non-secret, never baselined
- [ ] T032 **BLOCKED — needs the EC2 host; must be run by the author before merging. Production pre-flight — do not skip.** From `/home/ec2-user/FattoreStreet/deploy` on the host, run `sudo docker compose run --rm django python manage.py sync_blog_posts --dry-run` and confirm exactly `13 created, 7 updated`, with no `react` line. Any other journal creation means a slug is wrong and a real run would duplicate a live post. See [quickstart.md](./quickstart.md) step 6
- [ ] T034 **Pre-merge content review — the one gate `detect-secrets` cannot cover.** Read all thirteen files in `django/blog/learning-topics/` against FR-004 before merging. These are not public today (they are not on `main`), so merging is what exposes them. Looking for: private infrastructure identifiers (account IDs, ARNs, internal hostnames, bucket names, security-group IDs), and any weakness described as still-unfixed rather than historical. Highest-risk topics by subject: `111_…JWT_TRUST_BOUNDARY` (attack surface if `SECRET_KEY` leaks, recycled-user-ID authorization), `165_…OIDC_AUTHENTICATED_DUAL_REGISTRY…` (deploy role and registry wiring), `146_EPHEMERAL_FARGATE_ONE_SHOT_TASKS…` (task and schedule configuration). Redact or reword in place; the files are the source of truth, so an edit here is the fix
- [ ] T033 **BLOCKED — nothing is merged yet.** After merge, verify end to end per [quickstart.md](./quickstart.md) step 7: 13 posts under `llm-notebook`, 21 total, no duplicates against the T002 baseline, and the `synced 20 file(s)` line present in the deploy log between migrate and the health check

---

## Dependencies & Execution Order

```text
Phase 1 Setup (T001–T002)
    ↓
Phase 2 Foundational (T003–T005)  ← BLOCKS EVERYTHING
    ↓
Phase 3 US1 (T006–T022)  ← MVP, independently shippable
    ↓
Phase 4 US2 (T023–T025)  ← depends on US1's correctness
    ↓
Phase 5 US3 (T026–T027)  ← verification only, could run any time after Phase 2
    ↓
Phase 6 Polish (T028–T033)
```

**Story independence**: US1 stands alone — the command can be run by hand against production and delivers the whole reader-facing outcome. US2 removes the manual step. US3 is already built.

**Hard sequences**:

- T003 blocks everything (the file does not exist on this branch yet)
- T006 → T007 → T008 → T009 → T010 → T011 — same file, one after another
- T014 → … → T021 — same file, one after another
- T023 → T024 (verify after changing)
- T032 must precede the first unattended production run of the deploy step
- T034 must precede the merge itself — it is the only check on what the thirteen topics expose, and merging is what makes them public

## Parallel Opportunities

- **Phase 1**: T002 runs alongside T001
- **Phase 3**: T012 and T013 (two different post files) run in parallel with each other and with the T006–T011 command work; the test block can be written in parallel with the implementation block
- **Phase 5**: T026 and T027 together, and both can run any time after Phase 2
- **Phase 6**: T028, T029, and T031 are three different files and run together

## Implementation Strategy

**MVP = Phase 1 + Phase 2 + Phase 3.** At that point the twenty posts can be published by hand with one command, which is the entire reader-facing outcome. Everything after is about removing the human from the loop.

**Suggested increments**:

1. **Increment 1** (T001–T022): the command is correct. Verify with quickstart steps 1–4.
2. **Increment 2** (T023–T025): merging publishes. Verify with quickstart step 5.
3. **Increment 3** (T026–T033): docs, secrets scan, and the production pre-flight before the automated step is trusted unattended.

**Riskiest tasks, watch these**: T034 (the only review of what merging makes public, and unlike the others it cannot be undone by a redeploy), T032 (the only thing preventing duplicated live posts on the first real run), and T023 (fails silently — always verify with T024).
