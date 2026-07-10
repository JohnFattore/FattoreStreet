# Auto-Update Documentation

After making code changes, check whether any documentation needs updating. Do this silently as part of your normal workflow -- do NOT ask the user for permission to update docs.

## When to Update

Update docs when a change affects user-facing behavior, public APIs, setup, or architecture. For internal-only code churn, skip doc edits.

| Change Type | Files to Update |
|---|---|
| New/modified Django endpoint or URL pattern | `docs/API_REFERENCE.md` and `django/README.md` |
| New Django feature, Celery task, or service | `django/README.md` (features/apps list, env vars, Celery section) |
| New/modified Spring Boot endpoint | `docs/API_REFERENCE.md` and `springboot/README.md` (features list, usage examples) |
| New Spring Boot feature or service class | `springboot/README.md` (features list, env vars table, usage section) |
| New/modified React page, component, or RTK Query endpoint | `react-app/README.md` (pages list, key components, API layer section) |
| New dependency added (pip, npm, maven) | The relevant app README (`django/README.md`, `react-app/README.md`, `springboot/README.md`) |
| New app/service or major architectural change | `docs/ARCHITECTURE.md`, root `README.md`, and relevant app README |
| Changed setup steps, env vars, or run commands | `docs/GETTING_STARTED.md` and relevant app README |
| Spring Boot config, build, or setup change | `springboot/README.md` |
| LLM setup, model, or script change that affects usage (commands, flags, paths, prerequisites, outputs) | `llm/README.md` |

## How to Update

- Match the existing tone and formatting of each doc file
- For API endpoints: include method, path, description, parameters, and response shape
- For READMEs: keep sections concise; update version numbers if visible
- Do NOT rewrite entire files -- only add/modify the affected sections
- Do NOT create new doc files unless the user explicitly asks

## Skip When

- The change is purely internal (refactoring, tests, styling) with no public-facing impact
- The docs already accurately describe the new behavior
- The change only affects internal implementation details (naming, structure, comments) and does not change how users run, configure, or consume the feature
- The change is a minor script/internal tweak where commands, flags, paths, prerequisites, and outputs remain the same
