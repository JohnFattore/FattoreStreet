# Implementation Plan: LLM Notebook Blog Pipeline

**Branch**: `speckit-llm-notebook` | **Date**: 2026-08-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-llm-notebook-blog/spec.md`

## Summary

Twenty Markdown posts now live inside the Django app — seven journal posts in `django/blog/journal/` and thirteen learning topics in `django/blog/learning-topics/`, written by the CronJob routine and merged by PR. None of the learning topics exist in the blog database yet. This feature is the publish path and nothing else: finish the `sync_blog_posts` management command from PR #133 so it reads both directories, files everything from `learning-topics/` under the existing **LLM Notebook** category, and run it from `deploy/deploy.sh` on every merge to main.

The technical core is small — four changes, none of them large:

1. **`django/.dockerignore`** — two negation lines making the post directories' inclusion explicit. *(This was planned as the blocker on the belief that the bare `*.md` excluded them. It does not — see the correction in [research.md](./research.md) R-001. The lines were kept as documented insurance, not as a fix.)*
2. **`sync_blog_posts.py`** — two directories instead of one; slug, excerpt, and date derivation adapted to the version-stamp header format; directory-implied category.
3. **`deploy/deploy.sh`** — one line after the migrate line.
4. **Front matter on two journal files** — so they update the live posts they correspond to instead of duplicating them.

## Spec deltas

The spec was written before the posts moved into the Django app. Three requirements are now wrong and the plan supersedes them; `spec.md` is amended to match.

| Spec | Said | Now |
|---|---|---|
| FR-001 | Learning topics rewritten as essays for a general reader | Published verbatim in their study-guide register. The `blog-editor` skill makes this split deliberate: journal posts carry the author's voice, learning topics stay instructional. |
| FR-003 | Strip "files to read", line references, questions, exercises | These stay. They are the value of a learning topic. |
| User Story 1 | Authoring the thirteen posts is in scope | Already done — the files exist. Only publishing them remains. |

User Story 3 also lands differently than specified: the CronJob routine already opens the PR, and the `blog-editor` skill already records the format (FR-017). Nothing to build.

**What this leaves in scope**: User Story 2 end to end, plus the content-preservation guarantees (FR-009 through FR-014) that stop a deploy from damaging the eight posts already live.

## Technical Context

**Language/Version**: Python 3.14 (Django app), POSIX sh (deploy script)

**Primary Dependencies**: Django 5, DRF; no new dependency — front matter parsing stays hand-rolled, per PR #133

**Storage**: PostgreSQL (`blog_post`, `blog_category`, `blog_tag` and their join tables); Markdown files in the image are the source of truth

**Testing**: `django/tests/test_blog.py` via `uv run python manage.py test tests.test_blog`

**Target Platform**: Linux container on arm64 (t4g EC2 host), deployed via GHCR + SSM

**Project Type**: Web service — Django backend; no frontend change (the blog pages already render whatever the API serves)

**Performance Goals**: Not a factor. Twenty files, once per deploy; the whole import is well under a second and adds nothing measurable to deploy time.

**Constraints**:

- Must not duplicate, unpublish, or delete any of the eight posts already live
- Must be idempotent — the same commit deployed twice writes nothing the second time
- A failed import must fail the deploy loudly (`set -eu` in `deploy.sh` gives this for free)
- No secrets, private infrastructure identifiers, or unfixed security weaknesses in published content
- Commercially-free data only (`.claude/rules/data-licensing-commercial-free.md`)

**Scale/Scope**: 20 posts today, ~1 new learning topic per day, 4 categories

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` is the unmodified spec-kit template — no principles have been ratified, so there are no constitutional gates to evaluate. The project's actual governing rules live in `CLAUDE.md` and `.claude/rules/`, and the plan is checked against those instead:

| Rule | Gate | Status |
|---|---|---|
| `.claude/rules/auto-update-tests.md` | Modified management command ⇒ update `django/tests/test_blog.py` | Planned — see Phase 2 |
| `.claude/rules/auto-update-docs.md` | Changed run commands/env ⇒ update `django/README.md`; `BLOG_POSTS_DIR` removal ⇒ update `CLAUDE.md` | Planned |
| `.claude/rules/data-licensing-commercial-free.md` | Persisted + user-facing content must be commercially free | Pass — posts are the project's own writing about its own codebase |
| `.claude/rules/secrets-check.md` | detect-secrets must pass | Watch item — thirteen new posts discuss auth and infrastructure; the JWT topic quotes `SECRET_KEY` as an identifier, not a value |
| `CLAUDE.md` — Python follows PEP 8 | Style | Pass |

**Post-Phase-1 re-check**: no violations introduced. No new dependency, no new service, no new configuration surface — the design *removes* one (`BLOG_POSTS_DIR`).

## Project Structure

### Documentation (this feature)

```text
specs/001-llm-notebook-blog/
├── plan.md              # This file
├── spec.md              # Feature specification (amended, see Spec deltas)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output — includes the production pre-flight
├── contracts/
│   └── sync-blog-posts.md   # CLI contract for the management command
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
django/
├── .dockerignore                          # CHANGE: re-include the post directories
├── blog/
│   ├── journal/*.md                       # 7 posts; 2 gain slug front matter
│   ├── learning-topics/*.md               # 13 posts; unchanged
│   ├── management/commands/
│   │   └── sync_blog_posts.py             # CHANGE: the bulk of the work
│   └── models.py                          # unchanged — no migration
├── mysite/settings.py                     # CHANGE: drop BLOG_POSTS_DIR
├── tests/test_blog.py                     # CHANGE: extend coverage
└── README.md                              # CHANGE: document the command

deploy/deploy.sh                           # CHANGE: one line after migrate
CLAUDE.md                                  # CHANGE: drop the BLOG_POSTS_DIR env var
```

**Structure Decision**: Everything stays inside the existing `blog` app. The posts moving into `django/blog/` is what makes this feature small — the files ship in the image, so there is no bind mount, no host-clone dependency, and no build-context change. No new app, no new module, no model change, no migration.

## Implementation Approach

### Phase A — Get the files into the image

`django/.dockerignore` ends with a bare `*.md`. Append:

```text
!blog/journal/*.md
!blog/learning-topics/*.md
```

Nothing else works until this is right, and it fails silently — the build succeeds, the container just has no posts. Verify by listing the directory inside a built image, not by reading the file.

### Phase B — The command

Adapt `sync_blog_posts.py` from PR #133. Reuse its front matter parser, `parse_published_at`, `derive_title`, `get_or_create_terms`, and its create/update-by-slug core unchanged. What changes:

- **Two source directories**, resolved from the `blog` app's own location, each with a post type. `--path` still overrides for one-off imports. `settings.BLOG_POSTS_DIR` is deleted — it only existed to point outside the image.
- **Slug derivation**: filename stem, with a leading `<issue-number>_` stripped for learning topics. Front matter `slug:` still wins. Research R-003 has the measured comparison against the live slugs.
- **Excerpt derivation**: skip the header block (title, version stamp, source line) before taking the first paragraph. Without this every post's blurb is its version stamp.
- **Date derivation**: parse the version stamp's date as the default `published_at`. This is what makes the import idempotent and what interleaves the backfill chronologically instead of dumping twenty posts on today's date.
- **Directory-implied category**: `learning-topics/` ⇒ `LLM Notebook`, *added* to the post's categories rather than replacing them, so a category added by hand in the admin survives the next deploy.
- **Duplicate-slug detection** across both directories, raised before anything is written.

### Phase C — Wire it into the deploy

One line in `deploy/deploy.sh` after the migrate line:

```sh
docker compose run --rm django python manage.py sync_blog_posts
```

`set -eu` makes a failure fail the deploy; the SSM step already prints stdout and stderr; the workflow already gates on the final status. No error handling to write. `--publish` is deliberately not passed — stamp-derived dates cover the stamped posts, and an unstamped post should land as a draft rather than be published by accident.

### Phase D — Tests and docs

`django/tests/test_blog.py` gains coverage for the guarantees that protect the live site: the file-less post is untouched, re-import does not unpublish or re-date, learning topics get the LLM Notebook category, an admin-added category survives, duplicate slugs error before writing, and both mismatched journal slugs land on their existing posts. Then `django/README.md` and `CLAUDE.md` for the command and the removed env var.

## Risks

| Risk | Mitigation |
|---|---|
| First production run duplicates a live post | Mandatory `--dry-run` pre-flight against production before the automated step ever runs. [quickstart.md](./quickstart.md) has the exact expected output: 5 updated, 15 created. |
| Posts missing from the image, which fails silently | Lower than planned: `*.md` never excluded them (R-001 correction), and the negation lines now state the dependency. Still verify by listing the directory inside a built image — quickstart step 5, not yet run for want of a Docker daemon. |
| A learning topic leaks a secret, an infrastructure identifier, or an unfixed weakness | **Corrected**: an earlier draft of this row called the exposure pre-existing on the belief that the topics were already merged and public. They are not — `git log origin/main -- django/blog/learning-topics/` is empty; the thirteen files are staged on an unpushed branch. Merging this feature is what publishes them, so the risk is created here. CI's detect-secrets run catches literal credentials only; it cannot detect infrastructure identifiers or a described-but-unfixed weakness, and several topics walk through the JWT trust boundary, admin authorization, and deploy infrastructure. Mitigation is T034: a human read of all thirteen against FR-004 before merge. |
| Publishing rewrites a live post's title | Real and intended — files become source of truth. "Index Funds Rock!" becomes "Index Funds 101". Called out in the pre-flight so it is a decision, not a surprise. |

## Complexity Tracking

No constitutional violations to justify — no constitution is ratified, and the design adds no new project, dependency, or abstraction. It removes a setting.
