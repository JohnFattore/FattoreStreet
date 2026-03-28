---
name: Cursor rules should always be added to CLAUDE.md
description: When the user adds a new cursor rule, also add an @ import for it in CLAUDE.md so Claude Code picks it up
type: feedback
---

Always add new `.cursor/rules/*.mdc` files as `@` imports in the `## Behavior Rules` section of `CLAUDE.md`.

**Why:** User expects all cursor rules to apply to Claude Code automatically. Without the import in CLAUDE.md, Claude won't see the rule.

**How to apply:** Whenever a new `.mdc` file appears in `.cursor/rules/`, add `@.cursor/rules/<filename>.mdc` under the Behavior Rules section in CLAUDE.md without being asked.
