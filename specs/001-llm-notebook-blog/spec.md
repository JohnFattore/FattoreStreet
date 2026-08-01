# Feature Specification: LLM Notebook Blog Pipeline

**Feature Branch**: `speckit-llm-notebook`

**Created**: 2026-08-01

**Status**: Amended 2026-08-01 — see [plan.md](./plan.md) "Spec deltas"

**Input**: User description: "Turn every daily learning-topic GitHub issue into a published blog post on fattorestreet.com, written in the voice of the existing posts, filed under the LLM Notebook category, living as Django blog Post objects in production, published by the existing merge-to-main deploy, with the importer staying a Django management command."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A reader finds the LLM Notebook on the site (Priority: P1)

A visitor to fattorestreet.com opens the blog, filters to the "LLM Notebook" category, and finds a run of learning topics — one per topic the daily routine has produced. Clicking one shows the full post.

**Why this priority**: This is the entire point of the feature. Without published posts nothing else matters — the deploy plumbing only exists to serve this.

**Independent Test**: Import the posts into a database by hand and load the blog. If a reader can browse the LLM Notebook category and read every topic end to end, this story is delivered on its own — even with no automation behind it.

**Acceptance Scenarios**:

1. **Given** the thirteen learning-topic files exist in the repository, **When** a reader requests the blog post list filtered to the LLM Notebook category, **Then** thirteen posts are returned, each with a title and an excerpt.
2. **Given** a reader is looking at the LLM Notebook list, **When** they open any single post, **Then** they receive its full body, its category, and its tags.
3. **Given** the eight blog posts that were already live before this feature, **When** the reader browses the blog unfiltered, **Then** all eight are still present, still published, and appear exactly once each.

**Amended**: authoring the thirteen posts is no longer in scope — the files already exist in `django/blog/learning-topics/`, written by the CronJob routine. Only publishing them remains.

---

### User Story 2 - Merging to main publishes the posts (Priority: P2)

The author merges a pull request containing a new or edited post file. The existing deploy runs on its own, and by the time the deploy reports success the post is live on the site. Nobody logs into a server.

**Why this priority**: Without this the posts are just files in a repo. It is second only because a manual one-time import can prove Story 1 first.

**Independent Test**: Merge a branch that changes one post file and confirm the change is visible on the live site once the deploy finishes, with no human step in between.

**Acceptance Scenarios**:

1. **Given** a merge to main that adds a post file, **When** the deploy completes, **Then** the new post is retrievable from the live blog without any manual action.
2. **Given** a merge to main that edits the body of an already-published post, **When** the deploy completes, **Then** the live post shows the new body, keeps its original publication date, and remains published.
3. **Given** the same commit is deployed twice, **When** the second deploy completes, **Then** no post has been duplicated and no post has changed.
4. **Given** the post import fails partway through, **When** the deploy reports its result, **Then** the failure is visible in the deploy output rather than passing silently.

---

### User Story 3 - Tomorrow's learning topic lands in the repo by itself (Priority: P3)

The daily routine opens its learning-topic issue as it does today, and alongside it proposes the matching blog post as a change to the repository for the author to review. The author reads it, edits if needed, and merges — at which point Story 2 takes over and it goes live.

**Why this priority**: It turns a thirteen-post backfill into an ongoing habit. It is last because the backfill and the publish path are valuable immediately, and this depends on both being in place.

**Independent Test**: Run the daily routine once and confirm it produces both an issue and a reviewable repository change containing a post in the right voice, category, and format.

**Acceptance Scenarios**:

1. **Given** the daily routine runs, **When** it creates a learning-topic issue, **Then** it also proposes a matching post file for review rather than only opening the issue.
2. **Given** a proposed post from the routine, **When** the author reviews it, **Then** it already carries the LLM Notebook category and a slug that does not collide with any existing post.
3. **Given** the author never merges a proposed post, **When** the next deploy runs, **Then** nothing about the live blog changes.

---

### Edge Cases

- A post file's derived identifier matches a post already on the site that was written under a different name (the live "Index Funds Rock!" and "JSON Web Tokens" posts). The import must update those posts, not create second copies of them.
- A post exists on the live site with no file behind it (the live "React" post). The import must leave it untouched rather than deleting it.
- A post is edited after publication. Re-importing must not reset it to unpublished and must not move its publication date.
- Two post files claim the same identifier. This must be reported as an error before anything is written, not resolved silently by last-write-wins.
- A post names a category or tag that does not exist yet. It should be created rather than dropped, so a post is never published with its category silently missing.
- The deploy runs on a host whose copy of the post files is stale or missing. The import must fail loudly rather than publish an outdated set.
- A learning topic covers material that would embarrass or endanger the project if public — credentials, internal infrastructure identifiers, or a security weakness that is not yet fixed. Such material must not reach a published post.

## Requirements *(mandatory)*

### Functional Requirements

**Content**

- **FR-001** *(amended)*: Each of the thirteen existing learning topics MUST be published from its file in the repository. The files are authored by the CronJob routine in their instructional study-guide register and are published as written — rewriting them into blog voice is explicitly **not** wanted, and the two registers are kept deliberately distinct.
- **FR-002**: Each learning-topic post MUST be filed under the existing "LLM Notebook" category.
- **FR-003** *(amended — was the inverse)*: A learning-topic post MUST retain the study-guide structure of its source issue — the "files to read" lists, source references, questions, and exercises are the substance of the topic, not scaffolding to strip.
- **FR-004**: A learning-topic post MUST NOT contain credentials, secret values, private infrastructure identifiers, or the description of an unfixed security weakness.
- **FR-005** *(amended — the original demanded explicit identifiers everywhere; the built design derives them)*: Every post MUST have a slug derived deterministically from its filename, and MUST allow that slug to be overridden explicitly wherever derivation would not land on the post already live. Because a derived slug follows the filename, renaming a published post's file without an explicit override creates a second copy — so the rehearsal run (FR-014) is a required gate before any production import, not an optional convenience.
- **FR-006**: Posts MUST only contain material the project is free to publish commercially, per the project's data-licensing rule.

**Publishing**

- **FR-007**: The blog posts stored as files in the repository MUST become blog post records in the production database, readable through the site's public blog interface.
- **FR-008**: Publication MUST occur as part of the existing merge-to-main deploy, with no additional manual step.
- **FR-009**: The import MUST be idempotent: running it repeatedly against unchanged files MUST leave the site unchanged.
- **FR-010**: The import MUST match an incoming post to an existing one by its stable identifier and update it in place; it MUST NOT create a duplicate.
- **FR-011**: The import MUST NOT unpublish, reschedule, or delete any post that is already live, including posts that have no file behind them.
- **FR-012**: A failed import MUST fail the deploy visibly rather than being swallowed.
- **FR-013**: The importer MUST remain a management command of the Django application, runnable by hand against any environment, and MUST offer a rehearsal mode that reports exactly what it would change without changing anything.
- **FR-014**: Before the first production run, the operator MUST be able to see, from the rehearsal mode's output alone, which live posts would be updated and which would be created.

**Ongoing**

- **FR-015**: Each future daily learning topic MUST arrive in the repository as a reviewable proposed change containing its blog post, in addition to its GitHub issue.
- **FR-016**: A proposed post MUST NOT reach the live site until a human merges it.
- **FR-017**: The rules that define a valid learning-topic post — voice, category, identifier, required fields — MUST be recorded in the repository so the routine and any future author follow the same format.

### Key Entities

- **Learning topic**: The daily subject the routine picks out of the codebase. Today it exists only as a GitHub issue holding a study guide. It gains a second, public form: a blog post.
- **Blog post**: A titled, dated, categorised essay with a stable identifier, an excerpt, a body, and optional tags. Lives as a file in the repository and as a record in the production database; the file is the source of truth.
- **Category**: A named grouping shown to readers. "LLM Notebook" already exists and is the home for every learning-topic post.
- **Deploy**: The existing automated sequence triggered by a merge to main that brings production to the merged commit. It gains one more responsibility: reconciling the blog.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A reader can browse the LLM Notebook category on the live site and find all thirteen backfilled topics, each opening to a complete post.
- **SC-002** *(amended — "unedited" was wrong; the files are the source of truth, so they do edit their posts)*: The eight posts that were live before this feature remain live and unduplicated after the first production import, keeping their original publication dates, with their content reconciled from their files. Live post count goes from 8 to 21 with no post appearing twice. Two visible changes are expected and intended on that first run: every previously live post gains an excerpt (all eight are empty today), and `INDEX_FUNDS_101.md` retitles its post from "Index Funds Rock!" to "Index Funds 101" unless a `title:` override is added.
- **SC-003**: Publishing a new post takes the author zero actions beyond merging the pull request, and the post is live by the time the deploy reports success.
- **SC-004**: Re-running the import against unchanged content produces no change to any post, verified by a rehearsal run that reports zero creations and zero content updates.
- **SC-005** *(amended)*: Every learning-topic file in the repository has a corresponding published post; no topic is silently skipped by the import.
- **SC-006**: Every new learning-topic issue is accompanied by a proposed post in the repository — already satisfied by the CronJob routine.

## Assumptions

- **Publication on merge**: Posts go live as soon as they are merged. The pull-request review is the editorial gate; there is no second approval step after merge. The importer's rehearsal mode covers the "see it before it ships" need.
- **Dating**: Each backfilled learning-topic post is dated to the day its source issue was opened, so the LLM Notebook entries interleave chronologically with the existing posts rather than all landing on import day.
- **Backfill scope**: All thirteen learning-topic issues are in scope regardless of whether the issue is open or closed. Two are currently open; that has no bearing on whether the topic is worth publishing.
- **The issue stays**: Learning-topic issues continue to be created and continue to hold the study-guide form. The blog post is an additional public artefact, not a replacement, and the issue body is not edited by this feature.
- **Two registers, deliberately**: Journal posts carry the author's voice; learning topics stay instructional and are captured verbatim. The `blog-editor` skill owns both formats. This feature publishes what it finds and does not edit content.
- **Files are the source of truth**: Where a file and a live post disagree, the file wins. This can rewrite a published post's title.
- **Author attribution**: Learning-topic posts are attributed the same way the existing posts are; no new author identity is introduced.
- **The live "React" post**: It has no file behind it and is out of scope. It stays as it is, managed by hand, and the import leaves it alone.
- **Existing importer**: The management command already drafted in the open pull request #133 is the starting point; this feature completes and hardens it rather than replacing it.
- **Posts ship in the image** *(amended)*: The post files now live inside the Django app, so they travel with the built image rather than being read off the deploy host. No bind mount and no host-clone dependency — but the image's ignore rules must be corrected to stop excluding them.

## Dependencies

- The existing merge-to-main deploy pipeline must keep working; this feature extends it rather than replacing it.
- The "LLM Notebook" category must exist in production (it does).
- The daily learning-topic routine is configured outside this repository; Story 3 requires updating that configuration to consume the format recorded by FR-017.
