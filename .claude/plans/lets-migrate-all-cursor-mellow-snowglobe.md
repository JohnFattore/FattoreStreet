# Migrate Cursor skills & rules to Claude Code, remove `.cursor/`

## Context

The repo currently shares skills/rules between Cursor and Claude Code via cross-references: real content lives in `.cursor/skills/*/SKILL.md` and `.cursor/rules/*.mdc`, while `.claude/commands/*.md` and the CLAUDE.md files hold `@` pointer imports. The user is dropping Cursor, so all content moves into native Claude Code locations and `.cursor/` is deleted.

Claude Code natively supports both targets:
- **`.claude/skills/<name>/SKILL.md`** — project skills, invocable as `/name`, same `name`/`description` frontmatter format Cursor uses.
- **`.claude/rules/*.md`** — auto-loaded project rules; a `paths:` frontmatter list (glob patterns) scopes a rule to matching files, the direct equivalent of Cursor's `globs:`/`alwaysApply:`.

## 1. Skills → `.claude/skills/`

Move the 8 content skills verbatim (frontmatter already compatible):

| From | To |
|---|---|
| `.cursor/skills/<name>/SKILL.md` | `.claude/skills/<name>/SKILL.md` |

for: `code-review`, `create-pr`, `django-tests`, `react-tests`, `sec-equity-dividend-accuracy-pass`, `sec-etf-dividend-accuracy-pass`, `springboot-tests`, `ui-builder`.

Two skills point the other way (content in `.claude/commands/`, Cursor file is the pointer). Create `.claude/skills/<name>/SKILL.md` by combining the frontmatter from the Cursor pointer with the body from the command file:
- `commit` — frontmatter from `.cursor/skills/commit/SKILL.md` + body of `.claude/commands/commit.md`
- `secrets-from-arn` — frontmatter from `.cursor/skills/secrets-from-arn/SKILL.md` + body of `.claude/commands/secrets-from-arn.md`

Then **delete all 10 files in `.claude/commands/`** — skills provide the same `/name` invocation, so the command pointers are redundant (this also removes the `create-pr` "ignore the YAML frontmatter" wrapper hack, since skills parse frontmatter natively).

Update `.claude/agents/code-reviewer.md:33`: `@.cursor/skills/code-review/SKILL.md` → `@.claude/skills/code-review/SKILL.md`.

## 2. Rules → `.claude/rules/`

Convert each `.mdc` to `.md`, replacing the Cursor frontmatter (`description`, `globs`, `alwaysApply`) with either no frontmatter (always-on) or a `paths:` list (scoped). Body content is unchanged.

Always-on (no frontmatter):
- `auto-update-tests.md`, `auto-update-docs.md`, `data-licensing-commercial-free.md`

Path-scoped (`paths:` frontmatter):
- `django-drf.md` — `django/**/*.py`
- `react-typescript.md` — `react-app/**/*.{ts,tsx}`
- `springboot-java.md` — `springboot/**/*.java`
- `infrastructure.md` — `kubernetes/**`, `nginx/**`, `aws/**`, `**/Dockerfile`, `docker-compose*` (as a list; this rule was Cursor-only, previously not visible to Claude at all)
- `llm-local-ai.md` — `llm/**` (also previously Cursor-only)

Dropped, not migrated:
- `project-overview.mdc` — duplicates root CLAUDE.md. Fold its few unique, still-accurate bullets into root CLAUDE.md (the Infra bullet about `deploy/` GHCR/SSM/Terraform; the "No Hungarian notation / PEP 8 / TS strict" conventions if not already covered by the scoped rules). Skip its stale "legacy axios thunks" line (already migrated to RTK Query).
- `shared-skills.mdc` — describes the Cursor↔Claude cross-reference convention, obsolete.
- `RULE_INDEX.md` — Cursor-specific index; `.claude/rules/` filenames are self-describing.

## 3. CLAUDE.md updates

**Root `CLAUDE.md`:**
- Remove the `## Behavior Rules` section (the three `@.cursor/rules/*.mdc` imports) — those rules now auto-load from `.claude/rules/`.
- Remove the `## Shared Skills & Commands` section — replace with a short note: skills live in `.claude/skills/<name>/SKILL.md`, rules in `.claude/rules/*.md` (use `paths:` frontmatter to scope).
- Update `## Conventions`: "cursor rules (single source of truth)" → `.claude/rules/` .
- Fold in the unique project-overview bullets (step 2).

**Per-service `django/CLAUDE.md`, `react-app/CLAUDE.md`, `springboot/CLAUDE.md`:**
- Remove the `## Conventions` import (`@../.cursor/rules/<x>.mdc`) — the path-scoped rule now covers it and keeping both would double-load.
- Update the `## Writing Tests` import to `@../.claude/skills/<x>-tests/SKILL.md` (keeps test conventions in context when working in the service dir without invoking the skill).

## 4. Repo `memory/` directory

`memory/feedback_cursor_rules_in_claude_md.md` (git-tracked) instructs adding new `.mdc` files as CLAUDE.md imports — obsolete. Rewrite it (rename to `feedback_rules_in_claude_rules.md`): new rules go in `.claude/rules/*.md`, always-on rules need no frontmatter, scoped rules use `paths:`, no CLAUDE.md imports needed. Update the pointer line in `memory/MEMORY.md`.

## 5. Delete `.cursor/`

`git rm -r .cursor/` after everything above. Leave `.claude/plans/*` untouched (historical documents referencing old paths) and `docs/blog-posts/PILOT.md` (prose mention of Cursor as a tool, still accurate).

## Verification

1. `grep -rn "\.cursor" . --exclude-dir=node_modules --exclude-dir=.git` → only hits in `.claude/plans/` (historical) and none elsewhere.
2. Confirm every migrated file exists: 10 dirs under `.claude/skills/`, 8 files under `.claude/rules/`.
3. Fresh-session check: run `claude -p '/memory'`-style check or start a new session and run `/memory` — the three always-on rules should be listed; the skills list should show all 10 skills resolving without `.cursor` paths (e.g. `/django-tests` loads content, not a broken import).
4. Sanity-read expanded imports: `.claude/agents/code-reviewer.md` and the three per-service CLAUDE.md files reference only existing paths.
