---
name: code-review
description: Review code changes against FattoreStreet project conventions. Use when the user asks for a code review, PR review, or wants feedback on their changes.
---

# Code Review

Review code changes against the project's established conventions. Run `git diff` (or `git diff --staged`) to see what changed, then evaluate each file against the rules below.

## Review Checklist

For every changed file, check the applicable section:

### React (`react-app/**/*.{ts,tsx}`)

- [ ] No `className` on components -- styling belongs in `custom.scss` with page/component-scoped selectors
- [ ] Functional components with hooks; no class components
- [ ] Prefer named exports (`export function X`); avoid anonymous default exports (`export default () =>`)
- [ ] API calls use RTK Query from `functions/api.ts`, not raw fetch/axios
- [ ] Data-dependent UI wrapped in `<StateHandler>`
- [ ] Django responses converted from snake_case via `transformResponse`
- [ ] Interfaces defined in `interfaces.ts`
- [ ] No hardcoded API URLs -- use `VITE_APP_*` env vars

### Django (`django/**/*.py`)

- [ ] PEP 8 naming (snake_case everywhere)
- [ ] Type hints on function signatures
- [ ] Secrets come from `environ.Env`, never hardcoded
- [ ] Expensive external calls (yfinance, FRED) are cached
- [ ] Batch lookups use the `_partition_cached` pattern
- [ ] DRF serializers validate request/response data
- [ ] Responses use snake_case keys

### Spring Boot (`springboot/**/*.java`)

- [ ] Constructor injection, no `@Autowired` on fields
- [ ] Logging via SLF4J (`LoggerFactory.getLogger`)
- [ ] New SEC fields added to both `FIELD_TO_TAGS` and `STOCK_FIELDS` (if balance-sheet)
- [ ] Immutable collections (`Map.ofEntries`, `List.of`)
- [ ] Thin controllers; logic lives in `@Service` classes

## Feedback Format

Rate each finding:

- **Critical** -- must fix: bugs, security issues, broken conventions
- **Suggestion** -- should fix: style drift, missing error handling, optimization
- **Nit** -- optional: naming, minor readability

## Workflow

1. Run `git diff` to see all changes
2. Group findings by file
3. For each file, run through the applicable checklist above
4. Present findings grouped by severity, with code references
