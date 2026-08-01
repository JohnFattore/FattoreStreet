---
name: blog-editor
description: Edit a rough blog post draft into a polished version in the author's voice, or capture a learning topic as markdown in the repo. Journal posts live in django/blog/journal/, learning topics in django/blog/learning-topics/; both carry a repo version stamp and learning topics ship as a PR. This skill should be used when the user asks to edit, polish, revise, clean up, or finalize a blog post draft, or to write up a learning topic.
---

# Blog Editor

## Overview

Produce publish-ready Markdown that still sounds like the author. For journal posts the job is editing, not ghostwriting: tighten wording, fix structure and grammar, and preserve the author's ideas, examples, and personality. For learning topics the job is capture: get the study material into the repo in the house format.

Both types live inside the Django app so the container has them on disk. The files are the source of truth: `manage.py sync_blog_posts` runs on every deploy and imports them into `blog.Post` rows, which the public blog API then serves. Two consequences when writing one:

- **The filename becomes the public URL.** The slug is derived from it — a leading issue number is stripped, so `166_THE_SELF_HOSTED_GPU_INFERENCE_STACK.md` publishes at `the-self-hosted-gpu-inference-stack`. Renaming a published post's file orphans the live post and creates a second one; to change a slug safely, set `slug:` in front matter instead.
- **The version stamp's date becomes the publication date.** This is why the stamp rules below matter beyond provenance, and why a backfilled post stamps the commit matching its vintage rather than today's `HEAD`.

Learning topics are additionally filed under the **LLM Notebook** category, applied by the importer from the directory — do not add it as front matter.

## The two post types

Decide which type the work is before writing anything — it changes the directory, the filename, and how the work ships.

| | **Journal post** | **Learning topic** |
|---|---|---|
| What it is | The author's own writing — an explainer or an update on what they are working on | A structured study assignment on one mechanism in this repo: Overview, Files to read, Questions to answer, Primer, External docs, optional Exercise |
| Voice | The author's, preserved (see `references/voice.md`) | Instructional and neutral; matches the existing topics, not the blog voice |
| Lives in | `django/blog/journal/<SLUG>.md` | `django/blog/learning-topics/<NUMBER>_<SLUG>.md` |
| Filename | `SCREAMING_SNAKE_CASE` title | GitHub issue number, then the title in `SCREAMING_SNAKE_CASE`, truncated at a word boundary around 60 characters |
| Version stamp | Yes | Yes |
| Source line | No | Yes — links back to the originating issue, when there is one |
| Ships as | Edited in place, left uncommitted | A pull request |

Existing examples: `django/blog/journal/JSON_WEB_TOKEN.md`, `django/blog/learning-topics/165_OIDC_AUTHENTICATED_DUAL_REGISTRY_DOCKER_PUBLISH_SSM_BASED.md`. Read one of each before writing if the format is unclear.

## Workflow

### 1. Load the voice — journal posts only

Read `references/voice.md` for the distilled tone guide. If more calibration is needed, read one or two published journal posts (best exemplars: `django/blog/journal/ETFS_VS_MUTUAL_FUNDS.md`, `django/blog/journal/JSON_WEB_TOKEN.md`).

### 2. Read the source

For a journal post, read the full draft. Before editing, note:

- Which ideas, examples, and jokes are load-bearing — these must survive the edit
- Any factual claims, numbers, dates, or links — these must pass through unchanged
- Structural problems: sections out of order, missing definitions before first use of a term, weak or absent opening/closing

For a learning topic, read the source issue (`gh api repos/JohnFattore/FattoreStreet/issues/<n>`) or the author's notes, plus enough of the code it points at to confirm the file paths and line references still hold.

### 3. Write the file

**Journal post** — overwrite the draft at its path in `django/blog/journal/`. Apply the voice guide:

- Keep the author's structure unless a section is clearly misplaced; standardize headers to one `#` title with `##` sections
- Tighten each section into a single focused paragraph; cut repetition and filler
- Define terms and expand acronyms at first use if the draft skips this
- Keep colloquial flourishes and confident verdicts — they are the voice
- Fix typos, grammar, double spaces, and trailing whitespace
- Add nothing the author didn't say: no new claims, no invented examples, no padding

**Learning topic** — write a new file in `django/blog/learning-topics/`. Keep the issue body verbatim below the header block; this is capture, not rewriting. Strip the `Learning topic:` prefix from the title. If a draft started somewhere else, `git mv` it into place rather than copying, so history follows.

### 4. Stamp the repo version

Every post carries a version stamp linking to the exact state of the repo it describes on GitHub, so a reader knows which version the post is talking about. Get the values from git:

```bash
git rev-parse HEAD                  # full SHA — needed for the link URL
git rev-parse --short HEAD          # short SHA — the link text
git log -1 --format=%cs HEAD        # that commit's date, YYYY-MM-DD
```

Put the stamp on the line directly below the `#` title, as a single italic line linking to that commit's tree. A learning topic adds a source line under it. Blank line after the header block:

```markdown
# OIDC-authenticated dual-registry Docker publish + SSM-based EC2 deploy

_FattoreStreet @ [`b7d12439`](https://github.com/JohnFattore/FattoreStreet/tree/b7d12439fe7d4824d80a74e5dd788d7e50c00750) — 2026-07-30_

_Source: [#165](https://github.com/JohnFattore/FattoreStreet/issues/165)_
```

The URL is `https://github.com/JohnFattore/FattoreStreet/tree/<full-sha>` — the full 40-character SHA, so the link stays valid and pins the exact version. The link text is the short SHA.

Rules:

- One stamp per post, always immediately under the title — never a second one lower down
- If the post already has a stamp, overwrite it in place with the current values rather than appending
- Use `HEAD` at write time; do not invent, guess, or carry over a SHA from another post
- Only stamp a SHA that exists on `origin` — a commit that has not been pushed yet gives a 404 link. If `git branch -r --contains HEAD` is empty, use the newest pushed ancestor (`git merge-base HEAD origin/main`) and say so in the report
- If the working tree is dirty, still stamp `HEAD` and mention in the report that the post was stamped against the last commit, not the uncommitted changes
- When backfilling an old post, stamp the commit that matches the content's vintage — a journal post's own last commit, a learning topic's newest commit at or before the issue's `created_at` — not today's `HEAD`

### 5. Open a PR — learning topic only

A learning topic ships as a pull request, never as a commit straight to `main`. Use the `create-pr` skill, which branches, commits, pushes, and opens the PR. The PR should contain the topic file and nothing unrelated. Title it after the topic; in the body, say what it covers and link the source issue.

Journal posts skip this step — leave the edited file uncommitted for the author.

### 6. Report

Summarize for the author: which type it was, where the file landed, the PR link if one was opened, what changed structurally, notable wording changes, and — separately — anything flagged rather than fixed (suspect facts, missing links, stale file paths in a learning topic, sections that feel thin and might deserve more material only the author can supply).

## Boundaries

- Never alter factual claims, numbers, dates, or URLs; flag suspected errors instead
- Never relocate an already-published post; only a fresh draft moves into its directory
- Never hand-write or estimate the version stamp — the SHA, date, and link URL come from `git`, or the stamp does not get written
- Never merge the PR, and never push to `main`
- Do not rewrite a learning topic's issue body into blog voice — the two formats are deliberately different
- If a journal draft is fragmentary (outline or bullet notes rather than prose), point that out and ask whether to expand it — expanding bullets into prose crosses from editing into writing, and the author's phrasing is the raw material this skill exists to preserve
