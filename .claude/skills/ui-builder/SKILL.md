---
name: ui-builder
description: Build consistent React UI in react-app/ using React Bootstrap + custom.scss theme tokens, dark-mode safety, and repeatable page/component scaffolding recipes.
---

# UI Builder (React)

This skill is for quickly building new React pages/components with a consistent look and feel in `react-app/`, while staying aligned to the repo’s theming and styling approach.

## Stack and theming primitives

- **Components**: React Bootstrap (`react-bootstrap`)
- **Theme tokens**: CSS custom properties in `react-app/src/styles/custom.scss`:
  - `--primary`, `--secondary`, `--tertiary`, `--quaternary`
- **Dark mode**: `document.body.classList.toggle("dark-mode", darkMode)` (tokens flip under `.dark-mode`)

## Standards (non-negotiables)

### Colors and tokens

- **Use theme tokens** for any custom colors: `var(--primary|--secondary|--tertiary|--quaternary)`.
- **Do not hardcode colors in TSX** (no `#fff`, `rgb(...)`, etc.).
- **Dark-mode safe by construction**: prefer token colors so dark mode automatically works.

### Where styles live (decision tree)

Prefer, in order:

1. **Bootstrap utility classes** (`p-*`, `m-*`, `gap-*`, `d-flex`, `text-*`, `border-*`, `shadow-sm`, etc.)
2. **React Bootstrap props/components** (e.g. `variant`, `size`, `Container/Row/Col`, `Card`, `Alert`, `Modal`, `Table`)
3. **Scoped selectors in `custom.scss`** under a page/component root class (e.g. `.indexes-page { ... }`)
4. **Inline styles (moderate policy)** only when:
   - The value is **dynamic/computed** (e.g. width from state), or
   - It’s a small presentational block (roughly 1–3 properties) AND
   - Uses **tokens** (`var(--...)`) or safe CSS vars

Avoid:
- Large inline style objects for static layout or theming (move to utilities or `custom.scss` instead).
- Global CSS selectors that unintentionally affect multiple pages/components (scope under a root class).

### Layout conventions

- **Every page has a root wrapper class**: `<div className="some-page">...</div>`.
  - Add page-scoped styles under the same selector in `react-app/src/styles/custom.scss`.
- **Use Bootstrap grid** (`Container` → `Row` → `Col`) for responsive layout.
  - Default to `md` breakpoints; adjust to `sm/lg` based on density.
- **Spacing**: use Bootstrap spacing classes rather than ad-hoc pixel margins/padding.
  - Exceptions are fine in `custom.scss` for specific layouts, but keep the scale consistent.

## Reusable scaffolding recipes

Use these as building blocks when creating new UI.

### Recipe: Page shell (title + actions + content)

- Root wrapper: `<div className="feature-page">`
- Inside a `Container`:
  - **Row 1**: Title left, actions right (buttons, filters)
  - **Row 2+**: Content sections as `Card`s or `Alert`s
- State handling:
  - Prefer wrapping data-dependent content in `<StateHandler isLoading errors content />`
  - Loading: centered `Spinner` when the page is primarily empty
  - Errors: `Alert variant="danger"` with actionable copy
  - Empty: muted text or `Alert variant="secondary"` with next-step CTA

### Recipe: Card grid (feature tiles)

- Use `Row` with gutter utilities (e.g. `className="g-4"`) and `Col md={4}` / `Col lg={3}` depending on density.
- Keep card content consistent:
  - Title, short description, one primary link/button
  - Avoid mixed alignments within the same grid

### Recipe: Data table (numbers, sorting, loading)

- Use `react-bootstrap` `Table` (or the existing `SortableTable` when appropriate).
- Alignment:
  - Numeric columns **right-aligned**
  - Text columns **left-aligned**
- States:
  - Loading rows: a single centered spinner row or show `StateHandler` above the table
  - Empty: show a clear “No results” row with a suggestion (adjust filters, add item, etc.)

### Recipe: Form modal (create/edit flows)

- Use `Modal` + `Form` + standard spacing:
  - Field vertical rhythm via utilities (`mb-3`) or the existing modal-scoped patterns in `custom.scss`
- Footer actions:
  - Cancel (secondary) then Save/Submit (primary)
  - Disable submit while saving; show spinner inside submit button (CSS already adds margin for spinners inside `.btn`)
- Validation:
  - Show inline feedback where possible; reserve global `Alert` for submission errors

## Accessibility + UX checklist

- **Semantics**: use real `<Button>` / `<Link>` / `<Form>` controls; don’t make clickable `<div>` UI.
- **Labels**: form inputs have labels or `aria-label`.
  - Use `Form.Label` and `controlId` patterns.
- **Don’t rely on color alone**: pair color with text, icon, or position.
- **Keyboard**: modal forms support keyboard navigation; primary action is reachable and clearly labeled.

## Definition of Done (for any new UI)

- [ ] Uses Bootstrap utilities/React Bootstrap first; custom CSS is scoped under a root class in `custom.scss` when needed
- [ ] No hardcoded colors in TSX; uses theme tokens (`var(--...)`)
- [ ] Looks correct with and without `body.dark-mode`
- [ ] Responsive layout (grid/breakpoints)
- [ ] Loading/error/empty states are present and consistent
