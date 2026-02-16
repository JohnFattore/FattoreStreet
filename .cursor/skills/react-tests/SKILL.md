---
name: react-tests
description: Write Vitest tests for React components and pages using Testing Library, MSW, and RTK Query. Use when the user asks to create, add, or write tests for React components, pages, hooks, or frontend code in react-app/.
---

# React Test Writing

## Stack

Vitest + React Testing Library + MSW (Mock Service Worker) + happy-dom

## File Conventions

- Test files go in `react-app/__tests__/` named `ComponentName.test.tsx`
- MSW handlers live in `react-app/__tests__/mocks/handlers.ts`
- Config: `react-app/vitest.config.ts`
- Run: `cd react-app && npx vitest`

## Key Utilities

Import from `react-app/__tests__/testutils.tsx`:

```tsx
import { renderWithProviders, createTestStore } from "./testutils";
```

- `renderWithProviders(ui, { preloadedState })` -- wraps component in Redux Provider + MemoryRouter
- `createTestStore(preloadedState)` -- creates a store with RTK Query middleware

Authenticated state for protected components:

```tsx
const authenticatedState = {
  user: { access: "fake-token", refresh: "fake-refresh", username: "testuser" },
};
```

## MSW Mocking

Default handlers in `__tests__/mocks/handlers.ts` cover common endpoints (accounts, assets, asset-info, quote, fred-data, token, restaurants). For component-specific endpoints not already mocked, add per-test overrides:

```tsx
import { server } from "./mocks/server";
import { http } from "msw";

server.use(
  http.get("http://localhost:8000/api/my-endpoint/", () => {
    return Response.json({ key: "value" }, { status: 200 });
  })
);
```

Use `import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL` (and similar) for base URLs in handlers.

## Test Structure

```tsx
import { describe, it, expect } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "./testutils";
import MyComponent from "../src/components/MyComponent";

describe("MyComponent", () => {
  it("renders data after loading", async () => {
    renderWithProviders(<MyComponent />, { preloadedState: authenticatedState });

    // RTK Query triggers a fetch -- wait for async data
    expect(await screen.findByText("Expected Text")).toBeInTheDocument();
  });

  it("handles user interaction", async () => {
    const user = userEvent.setup();
    renderWithProviders(<MyComponent />);

    await user.click(screen.getByRole("button", { name: /submit/i }));
    await waitFor(() => {
      expect(screen.getByText("Success")).toBeInTheDocument();
    });
  });
});
```

## Workflow

1. **Read the component** -- identify which RTK Query hooks it uses and what props it needs
2. **Check handlers.ts** -- verify the endpoints are mocked; add `server.use()` overrides if not
3. **Write tests**:
   - Render with `renderWithProviders` and appropriate `preloadedState`
   - Assert loading state (spinner/text) appears first
   - Use `findByText` / `waitFor` for async data assertions
   - Use `userEvent` for interactions (clicks, typing, form submission)
   - Test error states by overriding MSW handlers to return error responses
4. **Run** -- `cd react-app && npx vitest --run` to execute once, or `npx vitest` for watch mode
