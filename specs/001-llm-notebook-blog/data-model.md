# Phase 1 Data Model: LLM Notebook Blog Pipeline

**Date**: 2026-08-01 | **Plan**: [plan.md](./plan.md)

No schema change and no migration. `blog.Post`, `blog.Category`, and `blog.Tag` are used exactly as they stand. What follows is the mapping from a Markdown file to a `Post` row, and the rules that keep the mapping safe to re-run.

## Source: the post file

Two post types, distinguished only by which directory the file is in.

| | Journal post | Learning topic |
|---|---|---|
| Directory | `django/blog/journal/` | `django/blog/learning-topics/` |
| Filename | `<TITLE_IN_CAPS>.md` | `<issue-number>_<TITLE_IN_CAPS>.md` |
| Implied category | none | `LLM Notebook` |
| Source line | absent | present |
| Count today | 7 | 13 |

Every file shares a header block, written by the `blog-editor` skill:

```markdown
# The Django↔Spring Boot JWT trust boundary
                                              ← blank
_FattoreStreet @ [`f337c3fe`](https://github.com/JohnFattore/FattoreStreet/tree/f337c3fe…) — 2026-07-19_
                                              ← blank
_Source: [#111](https://github.com/JohnFattore/FattoreStreet/issues/111)_   ← learning topics only
                                              ← blank
## Overview

FattoreStreet has two backend services — …
```

Front matter (`---` fenced `key: value` lines above the title) is **optional** and absent from all twenty files today. It stays supported as the escape hatch for the cases derivation cannot reach — currently two journal slugs.

## Mapping to `Post`

| Post field | Source | Rule |
|---|---|---|
| `slug` | front matter `slug:`, else filename | Filename stem lowercased, `_`→`-`, slugified. Learning topics strip a leading `<digits>_` first. **Decides create vs. update.** |
| `title` | front matter `title:`, else body | First `# ` heading. Every file has one. |
| `body_markdown` | body | Verbatim, including the header block — the version stamp is provenance a reader benefits from. |
| `excerpt` | front matter `excerpt:`, else body | First real paragraph, **skipping the header block**: heading lines, blank lines, and whole-line italic metadata (`_…_`). Truncated at 500 chars. |
| `published_at` | front matter `published_at:`, else version stamp | The stamp's trailing ` — YYYY-MM-DD`, midnight in the default timezone. Falls back to `--publish`/now, then to null (draft). **Never cleared once set.** |
| `cover_image_url` | front matter | Left as-is when absent. |
| `categories` | front matter + directory | Front matter list replaces; the directory-implied `LLM Notebook` is **added**, never replacing. |
| `tags` | front matter `tags:` | Replaces when the key is present; untouched when absent. |
| `author` | — | Not set by the import. Existing posts keep theirs. |
| `created_at` / `updated_at` | — | Managed by Django (`auto_now_add` / `auto_now`). |

Unknown category and tag names are created on demand via `get_or_create` keyed on slug, so a fresh dev database behaves like production, where `LLM Notebook` already exists as `llm-notebook`.

## Derived slugs

Measured against the eight posts live in production. Full comparison of derivation strategies in [research.md](./research.md) R-003.

**Journal** — filename-derived, five match as-is:

| File | Slug | Live post |
|---|---|---|
| `CLAUDE_CODE.md` | `claude-code` | updates |
| `DJANGO_MTV.md` | `django-mtv` | updates |
| `ETFS_VS_MUTUAL_FUNDS.md` | `etfs-vs-mutual-funds` | updates |
| `PILOT.md` | `pilot` | updates |
| `REDUX.md` | `redux` | updates |
| `INDEX_FUNDS_101.md` | `index-funds-rock` **via front matter** | updates |
| `JSON_WEB_TOKEN.md` | `json-web-tokens` **via front matter** | updates |

Without the two front matter overrides these derive `index-funds-101` and `json-web-token`, neither of which exists — producing duplicates of live posts rather than updating them.

**Learning topics** — all thirteen create new posts; the issue number is stripped so it does not appear in the public URL:

```text
111_THE_DJANGO_SPRING_BOOT_JWT_TRUST_BOUNDARY.md
  → the-django-spring-boot-jwt-trust-boundary
166_THE_SELF_HOSTED_GPU_INFERENCE_STACK_LLAMA_CPP_STABLE.md
  → the-self-hosted-gpu-inference-stack-llama-cpp-stable
```

**Not in either directory**: the live `react` post. No file maps to it, the command only touches slugs it has files for, so it is left exactly as it is.

## Expected end state

| | Before | After |
|---|---|---|
| Live posts | 8 | 21 |
| Created | — | 13 learning topics + 0 journal |
| Updated in place | — | 7 journal posts |
| Untouched | — | `react` |
| Duplicated | — | none |
| Unpublished | — | none |

The seven journal updates are content-preserving in body but do change two visible things for the first time: every post gains a real `excerpt` (all eight are empty today), and `INDEX_FUNDS_101.md` retitles its live post from "Index Funds Rock!" to "Index Funds 101".

## Invariants

These are what the tests in Phase D exist to hold:

1. **Slug is identity.** One file ⇒ at most one post, matched on slug. Two files claiming one slug is an error raised before any write.
2. **Never destructive.** The import creates and updates. It never deletes a post, never clears `published_at`, and never touches a post with no file behind it.
3. **Idempotent.** Every derived value is a pure function of file content, so re-running against unchanged files writes nothing. This is why dates come from the version stamp rather than `timezone.now()`.
4. **Additive categories.** A category added by hand in the admin survives the next deploy.
5. **Atomic per file.** Each file syncs in its own transaction; one malformed file fails that file and the command's exit code, without half-writing it.
