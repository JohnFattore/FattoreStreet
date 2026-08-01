# Phase 0 Research: LLM Notebook Blog Pipeline

**Date**: 2026-08-01 | **Plan**: [plan.md](./plan.md)

The spec was written against a repo layout that has since changed. This document records what changed, what that resolves, and the decisions the plan rests on.

## R-000: What moved since the spec was written

The posts are no longer in `docs/blog-posts/`. They now live inside the Django app, and all thirteen learning topics have already been written:

```text
django/blog/journal/*.md           7 files, git-renamed from docs/blog-posts/
django/blog/learning-topics/*.md  13 files, new — one per learning-topic issue
```

The `blog-editor` skill was rewritten to own this format, and it is explicit that the split is deliberate: journal posts keep the author's voice, learning topics stay in their instructional study-guide register and are captured verbatim from the issue.

**Consequence for the spec**: FR-001 and FR-003 said learning topics would be *rewritten* into blog voice with the study apparatus stripped. That is now wrong — they publish as-is. See "Spec deltas" in [plan.md](./plan.md). User Story 1's content-authoring work is already done and out of scope; this feature is now only the publish path (User Story 2), plus the ongoing automation (User Story 3), which the CronJob routine already handles by opening a PR.

## R-001: Getting the files into the container

**Decision**: Keep the files where they are and add two negation lines to `django/.dockerignore`.

**Rationale** *(corrected during implementation — see the note below)*: The django image builds with `context: django`, so `django/blog/journal/` and `django/blog/learning-topics/` are inside the build context already — the move solved that. `django/.dockerignore` contains a bare `*.md`, and the negation lines make the posts' inclusion explicit:

```text
*.md
!blog/journal/*.md
!blog/learning-topics/*.md
```

**Alternatives considered**:

- *Bind-mount the host's repo clone into the container.* This was the plan when posts lived in `docs/`. Now unnecessary, and strictly worse: it couples the running container to the host filesystem and makes the image non-self-describing — the same image would publish different content on different hosts.
- *Widen the django build context to the repo root.* Large change to `docker-build.yml` and the Dockerfile, slower builds, and pointless now that the files are already inside `django/`.
- *Drop `*.md` from `.dockerignore`.* Would pull `README.md`, `CLAUDE.md`, and every app's docs into the image for no reason.

**Correction (2026-08-01, during implementation)**: this research originally claimed the bare `*.md` line stripped every Markdown file from the context "regardless of directory", and the plan billed it as the blocker. That is wrong. Docker matches `.dockerignore` patterns against the full path relative to the context root using Go `filepath.Match` semantics, where `*` does not cross `/`. `*.md` therefore matches only root-level files — `django/README.md` and `django/CLAUDE.md` — and never `blog/journal/CLAUDE_CODE.md`. The posts would have reached the image with no change at all.

The negation lines were kept regardless: they are correct under either reading, they cost nothing, and they state the dependency outright so a later broadening of the pattern to `**/*.md` does not silently ship a container with no posts. What was genuinely load-bearing was the *directory move* into `django/blog/`, which happened before this feature started.

**Verification**: `docker build` the image, then `docker run --rm --entrypoint sh <image> -c 'ls blog/learning-topics | wc -l'` must print 13. Not yet run — no Docker daemon available in the implementation environment.

## R-002: Where the command looks for posts

**Decision**: Two directories, discovered relative to the `blog` app itself, with the learning-topics directory carrying an implied category.

**Rationale**: Because the posts now ship inside the app, the directory is a property of the code, not of the deployment — `Path(__file__)`-relative resolution works identically in the container, in a dev checkout, and in CI, with nothing to configure. `settings.BLOG_POSTS_DIR` from PR #133 exists only to point at a directory outside the image and can be dropped. `--path` survives for one-off imports.

**Alternatives considered**: Keeping `BLOG_POSTS_DIR` as an env var — rejected, it is now a setting with exactly one correct value and one more way for production to be misconfigured.

## R-003: Slugs, and the two live posts that would be duplicated

**Decision**: Derive journal slugs from the filename; derive learning-topic slugs from the filename with the leading issue number stripped. Add explicit `slug:` front matter to the two journal files whose derived slug does not match the post already live.

**Rationale**: There are eight posts already live. Whatever the command derives has to land on the existing slug or it creates a second copy. Measured against production:

| File | Filename-derived | Title-derived | Live slug | |
|---|---|---|---|---|
| `CLAUDE_CODE.md` | `claude-code` | `what-is-claude-code` | `claude-code` | filename ✓ |
| `DJANGO_MTV.md` | `django-mtv` | `djangos-model-template-view-system` | `django-mtv` | filename ✓ |
| `ETFS_VS_MUTUAL_FUNDS.md` | `etfs-vs-mutual-funds` | `etf-vs-mutual-funds` | `etfs-vs-mutual-funds` | filename ✓ |
| `PILOT.md` | `pilot` | `pilot` | `pilot` | both ✓ |
| `REDUX.md` | `redux` | `state-in-the-browser` | `redux` | filename ✓ |
| `INDEX_FUNDS_101.md` | `index-funds-101` | `index-funds-101` | `index-funds-rock` | **neither** |
| `JSON_WEB_TOKEN.md` | `json-web-token` | `json-web-tokens` | `json-web-tokens` | **filename ✗** |

Filename derivation gets five of seven; title derivation gets one. So: derive from filename, and override the two stragglers explicitly. `INDEX_FUNDS_101.md` has no derivation that reaches `index-funds-rock` — the live post was titled differently — so it must be explicit either way.

The command already supports `slug:` front matter (PR #133), so this needs no new code, just two files gaining a three-line header. FR-005 wanted stable identifiers set explicitly rather than inferred; this is a partial concession to convenience, and the mitigation is the pre-flight `--dry-run` (R-006) which makes any future mismatch visible before it writes.

**Note**: adopting the file as source of truth will also rewrite the live post's title — "Index Funds Rock!" becomes "Index Funds 101". That is the intended direction (files win) but it is a visible change to a published post, so it is called out in the quickstart's pre-flight step.

**Alternatives considered**:

- *Rename the two files to match the live slugs.* `git mv INDEX_FUNDS_101.md INDEX_FUNDS_ROCK.md` would work, but the `blog-editor` skill specifies the filename as the SCREAMING_SNAKE_CASE **title**, and the file's title is "Index Funds 101". Renaming would make the filename lie about the content to satisfy a legacy slug.
- *Rename the live posts to match the files.* Breaks any existing inbound link to `/blog/index-funds-rock`.

**The live `react` post** has no file behind it. The command only ever touches slugs it finds files for, so it is untouched — no code needed, but it is worth an explicit test (FR-011).

## R-004: Publication dates

**Decision**: Default `published_at` to the date in the post's version stamp, falling back to `--publish`/now only when no stamp is present.

**Rationale**: Every post carries a stamp on the line below its title, written by the `blog-editor` skill:

```markdown
_FattoreStreet @ [`f337c3fe`](https://github.com/.../tree/f337c3fe...) — 2026-07-19_
```

The stamp dates already run 2026-07-19 → 2026-07-30 across the thirteen topics, matching the vintage of the material. Reading the date from there satisfies the spec's dating assumption (backfilled posts interleave chronologically rather than all landing on import day) with **zero edits to any post file** and no front matter. It is also deterministic, which is what makes the import idempotent: a re-run computes the same date and writes no change. `--publish` with `timezone.now()` is not idempotent in the same way — it would stamp whatever moment the deploy happened to run.

**Alternatives considered**:

- *Add `published_at:` front matter to all twenty files.* Duplicates a date the stamp already carries, and creates a second source of truth that can drift from it.
- *Use `--publish` and `timezone.now()`.* All twenty posts get today's date, burying the eight existing posts under a wall of same-day entries.

**Edge case**: a post with no parseable stamp. Treat as "no explicit date" and fall back to the existing `--publish` behaviour rather than erroring — a missing stamp is a content problem for the `blog-editor` skill to catch, not a reason to fail a deploy.

## R-005: Excerpts

**Decision**: Skip the header block — title, version stamp, source line — when deriving the excerpt.

**Rationale**: PR #133's `derive_excerpt` returns the first non-heading line, which under the new format is always the version stamp. Every one of the twenty posts would get `_FattoreStreet @ [`f337c3fe`](...) — 2026-07-19_` as its blurb on the blog index. The fix is to skip italic-only metadata lines (`_..._`) and blank lines, then take the first real paragraph. For the learning topics that lands on the Overview paragraph, which is a genuinely good summary.

**Note**: all eight live posts currently have `excerpt: ""`. This import will populate them for the first time.

## R-006: Running it at deploy

**Decision**: One line in `deploy/deploy.sh`, immediately after the migrate line, using the same `docker compose run --rm django` form.

```sh
docker compose run --rm django python manage.py sync_blog_posts
```

**Rationale**: `deploy.sh` already runs migrations exactly this way, so this reuses an established, understood step. `set -eu` at the top of the script means a non-zero exit fails the deploy, the SSM step surfaces the output, and the workflow's final `[ "$status" = "Success" ]` fails the GitHub Actions run — FR-012 satisfied by the existing plumbing, with no error handling to write.

Ordering matters: after `migrate` (the tables must exist) and before the health check (a failed import should stop the deploy being called good). `--publish` is **not** passed; R-004's stamp-derived dates make it redundant for stamped posts, and leaving it off means an unstamped post lands as a draft rather than being published by accident.

**The first production run is the risky one** and gets a manual pre-flight instead: `--dry-run` against production before this ever runs unattended, to confirm the five expected updates and the fifteen expected creations. Covered in [quickstart.md](./quickstart.md).

**Alternatives considered**:

- *Run it from the container entrypoint on start.* Would run on every container restart, not just deploys, and races between multiple containers.
- *A separate GitHub Actions step hitting the host.* Duplicates the SSM plumbing `deploy.sh` already owns.

## R-007: Category assignment

**Decision**: Every post from `learning-topics/` gets the `LLM Notebook` category, applied by the command from the directory rather than written into each file. Journal posts get whatever their front matter says, and nothing if it says nothing.

**Rationale**: The category is a property of *which directory the file is in* — that is precisely what distinguishes the two post types. Encoding it in the command means the thirteen existing files need no edits, and the fourteenth topic the CronJob writes tomorrow is categorised correctly without the routine having to remember. `Category.objects.get_or_create` (already in PR #133's `get_or_create_terms`) resolves it, so a fresh dev database works the same as production, where the category already exists with slug `llm-notebook`.

**Idempotency caveat**: PR #133's `set_taxonomy` only calls `.set()` when the key is present in front matter, which correctly leaves categories added by hand in the admin alone. The directory-implied category must be *added* to the post's categories rather than replacing them, so that a learning topic given an extra category in the admin does not lose it on the next deploy.

**Alternatives considered**: Adding `categories: LLM Notebook` front matter to all thirteen files — thirteen edits now, and one more thing for the routine to get right on every future topic.

## R-008: Preserving what is already live

**Decision**: No new mechanism; PR #133's semantics are already correct and need locking down with tests.

The command matches on slug and updates in place, and its `published_at` handling only ever *sets*, never clears — so re-importing a post published by hand in the admin leaves it published. Combined with R-004's deterministic dates, a second run of the same commit is a genuine no-op. The gap is test coverage, not behaviour: `django/tests/test_blog.py` needs cases for the file-less live post, the re-import-does-not-unpublish path, and the duplicate-slug-across-directories error.

## Open questions

- **The two mismatched journal slugs** (R-003) are resolved by adding front matter, which makes `INDEX_FUNDS_101.md` the source of truth for a post currently titled "Index Funds Rock!" on the live site. If the author would rather keep the live title, the fix is one line in the file's front matter (`title: Index Funds Rock!`), not a code change. Flagged for the pre-flight step rather than blocking the plan.
