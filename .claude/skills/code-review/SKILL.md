---
name: code-review
description: Review code changes against FattoreStreet project conventions. Use when the user asks for a code review, PR review, or wants feedback on their changes.
---

# Code Review

Review code changes against the project's established conventions. Run `git diff` (or `git diff --staged`, or `git diff main...HEAD` on a branch) to see what changed, then evaluate each file against the rules below. The full conventions live in `.claude/rules/`; this checklist is the short form.

## Review Checklist

### Every change

- [ ] **Data licensing**: nothing sourced from yfinance (or any non-commercially-free provider) is stored, returned from an API, or rendered. yfinance is allowed only in dev-gated, ephemeral verification code
- [ ] No hardcoded secrets, keys, or connection strings; a detect-secrets false positive gets an inline `pragma: allowlist secret`, never a baseline entry
- [ ] Changed logic has matching test updates, and user-facing changes have matching doc updates (see `auto-update-tests.md`, `auto-update-docs.md`)

### React (`react-app/**/*.{ts,tsx}`)

- [ ] No custom `className` or `style` on components unless global styling in `custom.scss` is impossible; Bootstrap utility classes are fine
- [ ] Colors use theme tokens (`var(--primary)` etc.), never hardcoded values
- [ ] Functional components with hooks; named exports (`export function X`)
- [ ] API calls use RTK Query from `src/functions/api/`, not raw fetch/axios
- [ ] Data grids use `SortableTable` and the shared formatters in `helperFunctions.tsx`
- [ ] Data-dependent UI wrapped in `<StateHandler>`
- [ ] Django responses converted from snake_case via `transformResponse`; interfaces in `interfaces.ts`
- [ ] No hardcoded API URLs -- use `VITE_APP_*` env vars
- [ ] No control that triggers a backend job (no admin page)

### Django (`django/**/*.py`)

- [ ] PEP 8 naming; type hints on function signatures
- [ ] Secrets come from `environ.Env`
- [ ] Expensive external calls are cached; batch lookups use the `_partition_cached` pattern
- [ ] DRF serializers validate request/response data; responses use snake_case keys

### Spring Boot (`springboot/**/*.java`)

- [ ] No authenticated route and no new admin endpoint -- a new job gets an `APP_RUN_MODE` and a schedule
- [ ] Constructor injection; SLF4J logging; thin controllers with logic in `@Service` classes
- [ ] Will pass the `mvn verify` gates: Spotless formatting, no wildcard imports, `-Werror` clean in `src/main`, no unused injected fields, zoned `now()` via `MarketTime`, `JsonNode.asString()` not `asText()`
- [ ] New SEC fields added to both `FIELD_TO_TAGS` and `STOCK_FIELDS` (if balance-sheet)
- [ ] Lazy/bounded regex with a large alternation over filing text goes through `BoundedRegexInput`

## Feedback Format

Rate each finding:

- **Critical** -- must fix: bugs, security issues, licensing violations, broken conventions
- **Suggestion** -- should fix: style drift, missing error handling, missing tests
- **Nit** -- optional: naming, minor readability

For each finding give the file and line, the issue, and a concrete fix. If there are no findings, say so -- don't invent issues.

## Workflow

1. Run `git diff` to see all changes
2. Group findings by file
3. For each file, run through the applicable checklist above
4. Present findings grouped by severity, with code references
