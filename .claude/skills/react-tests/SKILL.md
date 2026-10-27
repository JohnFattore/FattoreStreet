---
name: react-tests
description: Write Vitest tests for React components and pages using Testing Library, MSW, and RTK Query. Use when the user asks to create, add, or write tests for React components, pages, hooks, or frontend code in react-app/.
---

# React Test Writing

## Stack

Vitest + React Testing Library + MSW (Mock Service Worker) + jsdom

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
- `renderWithRoute(...)` -- same, for components that read route params
- `createTestStore(preloadedState)` -- creates a store with RTK Query middleware

Authenticated state for protected components:

```tsx
const authenticatedState = {
  user: {
    access: "fake-token",
    refresh: "fake-refresh",
    username: "testuser",
    darkMode: false,
  },
};
```

## MSW Mocking

Default handlers in `__tests__/mocks/handlers.ts` cover most Django and Spring Boot endpoints the app calls. For a component-specific endpoint or an error case, add a per-test override. Build Django URLs from `VITE_APP_DJANGO_URL` plus the app prefix (`<app>/api/`), and Spring Boot URLs from `VITE_APP_SPRINGBOOT_URL`:

```tsx
import { server } from "./mocks/server";
import { http } from "msw";

const djangoBaseUrl = (import.meta.env.VITE_APP_DJANGO_URL || "").endsWith("/")
  ? import.meta.env.VITE_APP_DJANGO_URL
  : `${import.meta.env.VITE_APP_DJANGO_URL}/`;

server.use(
  http.get(djangoBaseUrl + "portfolio/api/my-endpoint/", () => {
    return Response.json({ key: "value" }, { status: 200 });
  })
);
```

## Test Structure

```tsx
import { describe, it, expect } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "./testutils";
import { MyComponent } from "../src/components/MyComponent";

describe("MyComponent", () => {
  it("renders data after loading", async () => {
    renderWithProviders(<MyComponent />, { preloadedState: authenticatedState });

    // RTK Query triggers a fetch -- wait for async data
    expect(await screen.findByText("Expected Text")).toBeInTheDocument();
  });

  it("handles user interaction", async () => {
    renderWithProviders(<MyComponent />);

    // Direct userEvent API, no .setup(), matching the existing tests
    await userEvent.click(screen.getByRole("button", { name: /submit/i }));
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
   - Use `userEvent` directly for interactions (clicks, typing, form submission)
   - Test error states by overriding MSW handlers to return error responses
4. **Run** -- `cd react-app && npx vitest --run` to execute once, or `npx vitest` for watch mode
