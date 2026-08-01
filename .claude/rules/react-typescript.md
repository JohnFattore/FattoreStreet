---
paths:
  - "react-app/**/*.{ts,tsx}"
---

# React + TypeScript Conventions

## Component Style

- Functional components only; use hooks (`useState`, `useMemo`, `useEffect`)
- Export named functions, not default exports: `export function MyComponent()`
- Use TypeScript generics for reusable components (see `SortableTable<T>`)
- Define prop types inline or as a `type` above the component

## Data tables

- Any **data grid** (multiple rows, column headers, and sortable or potentially sortable tabular content) must use **`SortableTable`** from [`react-app/src/components/SortableTable.tsx`](react-app/src/components/SortableTable.tsx): define `columns`, pass `data`, `isLoading`, and `errors`, and optional `initialSortKey` / `initialSortDirection`. Extend behavior by composing around `SortableTable` or by evolving that shared component—do not introduce a parallel `react-bootstrap` `<Table>` plus custom sort state for the same pattern.
- **Column cell formatting**: prefer shared helpers from [`react-app/src/functions/helperFunctions.tsx`](react-app/src/functions/helperFunctions.tsx)—most tables use **`formatString(value, type)`** in `render` with `type` one of `"text"`, `"money"`, `"amount"`, `"percent"` (and `"date"` where already used). For large magnitudes or index-style metrics, use **`formatNumber`**, **`formatCurrency`**, **`formatPercent`**, **`formatLargeCurrency`**, and **`formatLargeNumber`** as in existing tables. Use **`getApiErrorMessages`** (or **`getErrorMessages`**) when normalizing RTK Query errors for `errors` or display. Only use ad-hoc `Intl` formatters in a table when the shared helpers cannot express the display (e.g. extra decimal places for prices).
- **Exceptions**: purely static, non-sortable markup (e.g. small label/value blocks) where a full grid is unnecessary—prefer simpler layout first; use raw `<Table>` only when `SortableTable` does not fit.

## Styling — No className

- **Do NOT add custom `className` to components.** Style React Bootstrap components globally in `react-app/src/styles/custom.scss` instead.
- The project uses Bootstrap 5 via `react-bootstrap`. Use Bootstrap component props (`variant`, `size`, etc.) rather than custom CSS classes.
- Bootstrap utility classes (`text-center`, `mb-3`, `d-flex`, `fw-bold`, etc.) are acceptable when they avoid the need for custom CSS. Prefer them over adding new rules to `custom.scss` for one-off spacing or layout tweaks.
- If a new style is needed that utilities cannot express, add it to `custom.scss` targeting the Bootstrap class or HTML element directly.
- Theme colors use CSS custom properties: `--primary`, `--secondary`, `--tertiary`, `--quaternary` (with `.dark-mode` overrides).
- Only use a custom `className` or `style` prop as a last resort when global styling is truly impossible.

## State & Data Fetching

- Use **RTK Query** (defined in `react-app/src/functions/api.ts`) for all API calls
- Custom Axios base query handles JWT auth automatically from Redux state
- Django responses are snake_case; use `transformResponse` to convert to camelCase interfaces
- Spring Boot URLs use the `baseUrl` override in the query config
- Interfaces live in `react-app/src/interfaces.ts`

## No Spring Boot admin panel

There is no admin page and no authenticated Spring Boot route. The `/admin/**` endpoints were
retired; every data-loading job now runs as a scheduled Fargate one-shot selected by
`APP_RUN_MODE` (see `springboot/deploy/terraform/README.md`). Do not add a React control to
trigger a backend job, and do not reintroduce `Admin.tsx` -- a new job gets a run mode and a
schedule, not a button. `/react-admin/success-bar` (`AdminSuccessBar`) is unrelated: it is a
read-only diagnostics view and stays.

## Error Handling

- Wrap data-dependent UI in `<StateHandler>` (handles loading spinners and error display)
- RTK Query errors should flow through the existing `error` patterns, not be swallowed silently
