---
name: blog-editor
description: Edit a rough blog post draft in docs/blog-posts/ into a polished version in the author's voice, replacing the draft in place. This skill should be used when the user asks to edit, polish, revise, clean up, or finalize a blog post draft.
---

# Blog Editor

## Overview

Take a rough blog post draft (normally a Markdown file in `docs/blog-posts/`) and rewrite it in place as a publish-ready post that still sounds like the author. The job is editing, not ghostwriting: tighten wording, fix structure and grammar, and preserve the author's ideas, examples, and personality.

## Workflow

### 1. Load the voice

Read `references/voice.md` for the distilled tone guide. If more calibration is needed, read one or two published posts in `docs/blog-posts/` (best exemplars: `ETFS_VS_MUTUAL_FUNDS.md`, `JSON_WEB_TOKEN.md`).

### 2. Read the draft

Read the full draft. Before editing, note:

- Which ideas, examples, and jokes are load-bearing — these must survive the edit
- Any factual claims, numbers, dates, or links — these must pass through unchanged
- Structural problems: sections out of order, missing definitions before first use of a term, weak or absent opening/closing

### 3. Edit in place

Overwrite the draft file with the edited version (same file path — the edited post replaces the rough draft). Apply the voice guide:

- Keep the author's structure unless a section is clearly misplaced; standardize headers to one `#` title with `##` sections
- Tighten each section into a single focused paragraph; cut repetition and filler
- Define terms and expand acronyms at first use if the draft skips this
- Keep colloquial flourishes and confident verdicts — they are the voice
- Fix typos, grammar, double spaces, and trailing whitespace
- Add nothing the author didn't say: no new claims, no invented examples, no padding

### 4. Report

Summarize the edit for the author: what changed structurally, notable wording changes, and — separately — anything flagged rather than fixed (suspect facts, missing links, sections that feel thin and might deserve more material only the author can supply).

## Boundaries

- Never alter factual claims, numbers, dates, or URLs; flag suspected errors instead
- Never change the file's location or name; the edited version replaces the draft in place
- If the draft is fragmentary (outline or bullet notes rather than prose), point that out and ask whether to expand it — expanding bullets into prose crosses from editing into writing, and the author's phrasing is the raw material this skill exists to preserve
