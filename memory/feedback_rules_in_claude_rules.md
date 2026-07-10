---
name: Project rules live in .claude/rules
description: New project rules go in .claude/rules/*.md; they auto-load, so no CLAUDE.md imports are needed
type: feedback
---

Put new project rules in `.claude/rules/<topic>.md`. Rules without frontmatter load every session; add a `paths:` frontmatter list of globs to scope a rule to matching files (e.g. `django/**/*.py`).

**Why:** User expects all project rules to apply to Claude Code automatically. `.claude/rules/` is auto-discovered, so no `@` imports in CLAUDE.md are needed (the old `.cursor/rules/` + CLAUDE.md import setup was migrated in July 2026).

**How to apply:** When adding a convention or behavior rule, create it directly in `.claude/rules/` — always-on rules get no frontmatter, service-specific rules get `paths:` globs. Do not add `@` imports for rules to CLAUDE.md.
