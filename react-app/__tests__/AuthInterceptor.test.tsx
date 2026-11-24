// @vitest-environment jsdom
import { act, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { http } from "msw";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import App from "../src/App.tsx";
import { djangoApi, springbootApi } from "../src/functions/api";
import {
  decodeJwtExpMs,
  getSessionExpiryMs,
  hasActiveSession,
  isJwtExpired,
} from "../src/functions/auth";
import { sanitizePersistedState } from "../src/store";
import { createTestStore } from "./testutils";
import { makeTestJwt } from "./mocks/handlers";
import { server } from "./mocks/server";

const djangoBaseUrl = (import.meta.env.VITE_APP_DJANGO_URL || "").endsWith("/")
  ? import.meta.env.VITE_APP_DJANGO_URL
  : `${import.meta.env.VITE_APP_DJANGO_URL}/`;
const springbootBaseUrl = (
  import.meta.env.VITE_APP_SPRINGBOOT_URL || ""
).endsWith("/")
  ? import.meta.env.VITE_APP_SPRINGBOOT_URL
  : `${import.meta.env.VITE_APP_SPRINGBOOT_URL}/`;

const meUrl = djangoBaseUrl.concat("users/api/me/");
const refreshUrl = djangoBaseUrl.concat("users/api/token/refresh/");
const accountsUrl = djangoBaseUrl.concat("portfolio/api/accounts/");
const assetsUrl = djangoBaseUrl.concat("portfolio/api/assets/");

const expiredAccessBody = {
  detail: "Given token not valid for any token type",
  code: "token_not_valid",
  messages: [
    {
      token_class: "AccessToken",
      token_type: "access",
      message: "Token is expired",
    },
  ],
};

function renderAppAt(initialEntry: string, preloadedState: object) {
  const store = createTestStore(preloadedState);
  const view = render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <App />
      </MemoryRouter>
    </Provider>,
  );
  return { ...view, store };
}

afterEach(() => {
  vi.useRealTimers();
});

describe("JWT auth helpers", () => {
  it("decodes numeric exp claims and ignores non-JWT strings", () => {
    const token = makeTestJwt({ exp: 2_000_000_000 });
    expect(decodeJwtExpMs(token)).toBe(2_000_000_000 * 1000);
    expect(decodeJwtExpMs("opaque-test-token")).toBeNull();
    expect(decodeJwtExpMs("")).toBeNull();
  });

  it("evaluates expiry with skew and active session state", () => {
    const nowMs = 1_800_000_000_000;
    const nowSec = Math.floor(nowMs / 1000);
    const expiredToken = makeTestJwt({ exp: nowSec - 10 });
    const expiringSoonToken = makeTestJwt({ exp: nowSec + 15 });
    const validToken = makeTestJwt({ exp: nowSec + 3600 });

    expect(isJwtExpired(expiredToken, 0, nowMs)).toBe(true);
    expect(isJwtExpired(expiringSoonToken, 30_000, nowMs)).toBe(true);
    expect(isJwtExpired(validToken, 30_000, nowMs)).toBe(false);
    expect(isJwtExpired("opaque-token", 0, nowMs)).toBe(false);

    // Expired access + valid refresh is still an active (renewable) session.
    expect(
      hasActiveSession({ access: expiredToken, refresh: validToken }, nowMs),
    ).toBe(true);
    // Expired refresh ends the session even if access hasn't expired.
    expect(
      hasActiveSession({ access: validToken, refresh: expiredToken }, nowMs),
    ).toBe(false);
    expect(
      getSessionExpiryMs({ access: expiredToken, refresh: validToken }),
    ).toBe((nowSec + 3600) * 1000);
  });
});

describe("sanitizePersistedState", () => {
  it("clears expired sessions on rehydration while preserving darkMode", () => {
    const nowSec = Math.floor(Date.now() / 1000);
    const persisted = sanitizePersistedState({
      _persist: { version: 1, rehydrated: true },
      user: {
        username: "spike",
        access: makeTestJwt({ exp: nowSec - 300 }),
        refresh: makeTestJwt({ exp: nowSec - 60 }),
        darkMode: true,
        loading: false,
        error: "old",
      },
    } as never) as unknown as {
      user: Record<string, unknown>;
    };

    expect(persisted.user).toEqual({
      username: "",
      access: "",
      refresh: "",
      darkMode: true,
    });
  });

  it("preserves a session whose access expired when refresh is still valid", () => {
    const nowSec = Math.floor(Date.now() / 1000);
    const expiredAccess = makeTestJwt({ exp: nowSec - 60 });
    const validRefresh = makeTestJwt({ exp: nowSec + 3600 });

    const persisted = sanitizePersistedState({
      _persist: { version: 1, rehydrated: true },
      user: {
        username: "spike",
        access: expiredAccess,
        refresh: validRefresh,
        darkMode: false,
      },
    } as never) as unknown as {
      user: Record<string, unknown>;
    };

    expect(persisted.user.username).toBe("spike");
    expect(persisted.user.access).toBe(expiredAccess);
    expect(persisted.user.refresh).toBe(validRefresh);
  });
});

describe("RTK Query baseQuery auth refresh and 401 handling", () => {
  it("catches a mount-time 401 on /users/api/me/ on /admin, refreshes, and renders the dashboard", async () => {
    let refreshCalls = 0;
    const meAuthHeaders: (string | null)[] = [];
    server.use(
      http.post(refreshUrl, () => {
        refreshCalls += 1;
        return Response.json({ access: "new-access" });
      }),
      http.get(meUrl, ({ request }) => {
        const auth = request.headers.get("Authorization");
        meAuthHeaders.push(auth);
        return auth === "Bearer new-access"
          ? Response.json({
              id: 1,
              username: "spike",
              email: "spike@example.com",
              is_staff: true,
            })
          : Response.json(expiredAccessBody, { status: 401 });
      }),
    );

    const { store } = renderAppAt("/admin", {
      user: {
        username: "spike",
        access: "stale-access",
        refresh: "valid-refresh",
        darkMode: false,
      },
    });

    expect(
      await screen.findByText("Admin Monitoring Dashboard"),
    ).toBeInTheDocument();
    expect(refreshCalls).toBe(1);
    expect(meAuthHeaders).toEqual(["Bearer stale-access", "Bearer new-access"]);
    expect(store.getState().user.access).toBe("new-access");
    expect(store.getState().user.refresh).toBe("valid-refresh");
  });

  it("proactively refreshes an expired JWT access token before calling /users/api/me/ on /admin", async () => {
    const nowSec = Math.floor(Date.now() / 1000);
    const expiredAccess = makeTestJwt({ exp: nowSec - 60 });
    const validRefresh = makeTestJwt({ exp: nowSec + 3600 });
    const freshAccess = makeTestJwt({ exp: nowSec + 300 });

    let refreshCalls = 0;
    const meAuthHeaders: (string | null)[] = [];
    server.use(
      http.post(refreshUrl, () => {
        refreshCalls += 1;
        return Response.json({ access: freshAccess });
      }),
      http.get(meUrl, ({ request }) => {
        meAuthHeaders.push(request.headers.get("Authorization"));
        return Response.json({
          id: 1,
          username: "spike",
          email: "spike@example.com",
          is_staff: true,
        });
      }),
    );

    const { store } = renderAppAt("/admin", {
      user: {
        username: "spike",
        access: expiredAccess,
        refresh: validRefresh,
        darkMode: false,
      },
    });

    expect(
      await screen.findByText("Admin Monitoring Dashboard"),
    ).toBeInTheDocument();
    expect(refreshCalls).toBe(1);
    // Never sent the expired access token (0 unnecessary 401s).
    expect(meAuthHeaders).toEqual([`Bearer ${freshAccess}`]);
    expect(store.getState().user.access).toBe(freshAccess);
  });

  it("logs out and prompts sign-in on /admin when mount-time /users/api/me/ 401s and refresh fails", async () => {
    server.use(
      http.post(refreshUrl, () =>
        Response.json(
          { detail: "Token is expired", code: "token_not_valid" },
          { status: 401 },
        ),
      ),
      http.get(meUrl, () => Response.json(expiredAccessBody, { status: 401 })),
    );

    const { store } = renderAppAt("/admin", {
      user: {
        username: "spike",
        access: "stale-access",
        refresh: "expired-refresh",
        darkMode: false,
      },
    });

    expect(await screen.findByText("Sign in required")).toBeInTheDocument();
    expect(screen.queryByText("Error!")).not.toBeInTheDocument();
    expect(store.getState().user.username).toBe("");
    expect(store.getState().user.access).toBe("");
    expect(store.getState().user.refresh).toBe("");
    expect(Object.keys(store.getState().djangoApi.queries)).toHaveLength(0);
  });

  it("deduplicates concurrent 401 refreshes into a single refresh request", async () => {
    let refreshCalls = 0;
    server.use(
      http.post(refreshUrl, async () => {
        refreshCalls += 1;
        await new Promise((resolve) => setTimeout(resolve, 10));
        return Response.json({ access: "new-access" });
      }),
      http.get(accountsUrl, ({ request }) =>
        request.headers.get("Authorization") === "Bearer new-access"
          ? Response.json([
              { id: 1, name: "Brokerage", account_type: "TAXABLE_ACCOUNT" },
            ])
          : Response.json(expiredAccessBody, { status: 401 }),
      ),
      http.get(assetsUrl, ({ request }) =>
        request.headers.get("Authorization") === "Bearer new-access"
          ? Response.json([])
          : Response.json(expiredAccessBody, { status: 401 }),
      ),
    );

    const store = createTestStore({
      user: {
        username: "spike",
        access: "old-access",
        refresh: "valid-refresh",
        darkMode: false,
      },
    });

    const [accountsRes, assetsRes] = await Promise.all([
      store.dispatch(djangoApi.endpoints.getAccounts.initiate()),
      store.dispatch(djangoApi.endpoints.getAssets.initiate()),
    ]);

    expect(accountsRes.isSuccess).toBe(true);
    expect(assetsRes.isSuccess).toBe(true);
    expect(refreshCalls).toBe(1);
    expect(store.getState().user.access).toBe("new-access");
  });

  it("does not refresh a second time when the retried request also 401s", async () => {
    let refreshCalls = 0;
    let accountsCalls = 0;
    server.use(
      http.post(refreshUrl, () => {
        refreshCalls += 1;
        return Response.json({ access: "new-access" });
      }),
      http.get(accountsUrl, () => {
        accountsCalls += 1;
        return Response.json(expiredAccessBody, { status: 401 });
      }),
    );

    const store = createTestStore({
      user: {
        username: "spike",
        access: "old-access",
        refresh: "valid-refresh",
        darkMode: false,
      },
    });

    const result = await store.dispatch(
      djangoApi.endpoints.getAccounts.initiate(),
    );

    expect(result.isError).toBe(true);
    expect(result.error).toMatchObject({ status: 401 });
    expect(refreshCalls).toBe(1);
    expect(accountsCalls).toBe(2);
  });

  it("does not log out when the refresh endpoint returns a transient 503", async () => {
    server.use(
      http.post(refreshUrl, () =>
        Response.json({ detail: "Service unavailable" }, { status: 503 }),
      ),
      http.get(accountsUrl, () =>
        Response.json(expiredAccessBody, { status: 401 }),
      ),
    );

    const store = createTestStore({
      user: {
        username: "spike",
        access: "old-access",
        refresh: "valid-refresh",
        darkMode: false,
      },
    });

    const result = await store.dispatch(
      djangoApi.endpoints.getAccounts.initiate(),
    );

    expect(result.isError).toBe(true);
    expect(result.error).toMatchObject({ status: 503 });
    expect(store.getState().user.access).toBe("old-access");
    expect(store.getState().user.refresh).toBe("valid-refresh");
  });

  it("logs out when an authenticated request 401s with no refresh token", async () => {
    let refreshCalls = 0;
    server.use(
      http.post(refreshUrl, () => {
        refreshCalls += 1;
        return Response.json({ access: "new-access" });
      }),
      http.get(accountsUrl, () =>
        Response.json(expiredAccessBody, { status: 401 }),
      ),
    );

    const store = createTestStore({
      user: {
        username: "spike",
        access: "stale-access",
        refresh: "",
        darkMode: false,
      },
    });

    await store.dispatch(djangoApi.endpoints.getAccounts.initiate());

    expect(refreshCalls).toBe(0);
    expect(store.getState().user.username).toBe("");
    expect(store.getState().user.access).toBe("");
    expect(Object.keys(store.getState().djangoApi.queries)).toHaveLength(0);
  });

  it("passes anonymous 401 responses through without attempting refresh", async () => {
    let refreshCalls = 0;
    server.use(
      http.post(refreshUrl, () => {
        refreshCalls += 1;
        return Response.json({ access: "new-access" });
      }),
      http.get(accountsUrl, () =>
        Response.json(
          { detail: "Authentication credentials were not provided." },
          { status: 401 },
        ),
      ),
    );

    const store = createTestStore({
      user: { username: "", access: "", refresh: "", darkMode: false },
    });

    const result = await store.dispatch(
      djangoApi.endpoints.getAccounts.initiate(),
    );

    expect(result.isError).toBe(true);
    expect(result.error).toMatchObject({ status: 401 });
    expect(refreshCalls).toBe(0);
  });

  it("does not attach Authorization headers to public Spring Boot requests", async () => {
    let observedAuthHeader: string | null = "unset";
    server.use(
      http.get(springbootBaseUrl.concat("indexes"), ({ request }) => {
        observedAuthHeader = request.headers.get("Authorization");
        return Response.json([]);
      }),
    );

    const store = createTestStore({
      user: {
        username: "spike",
        access: "test-access",
        refresh: "test-refresh",
        darkMode: false,
      },
    });

    await store.dispatch(springbootApi.endpoints.getIndexes.initiate());
    expect(observedAuthHeader).toBeNull();
  });
});

describe("App session expiry monitor", () => {
  it("logs out automatically on /user when the refresh token expires without hitting a 401", async () => {
    vi.useFakeTimers();
    const baseMs = 1_800_000_000_000;
    vi.setSystemTime(baseMs);
    const nowSec = Math.floor(baseMs / 1000);

    const { store } = renderAppAt("/user", {
      user: {
        username: "spike",
        access: makeTestJwt({ exp: nowSec + 300 }),
        refresh: makeTestJwt({ exp: nowSec + 60 }),
        darkMode: false,
      },
    });

    expect(screen.getByText("User Profile")).toBeInTheDocument();

    await act(async () => {
      vi.advanceTimersByTime(61_000);
    });

    expect(store.getState().user.access).toBe("");
    expect(store.getState().user.refresh).toBe("");
    expect(store.getState().user.username).toBe("");
    expect(screen.getByText("Sign In")).toBeInTheDocument();
  });

  it("clears an expired session on window focus after sleep", async () => {
    const nowMs = Date.now();
    const nowSec = Math.floor(nowMs / 1000);
    const dateSpy = vi.spyOn(Date, "now").mockReturnValue(nowMs);

    try {
      const { store } = renderAppAt("/user", {
        user: {
          username: "spike",
          access: makeTestJwt({ exp: nowSec + 300 }),
          refresh: makeTestJwt({ exp: nowSec + 3600 }),
          darkMode: false,
        },
      });

      expect(screen.getByText("User Profile")).toBeInTheDocument();

      // Simulate clock advancing past refresh expiry while the machine slept.
      dateSpy.mockReturnValue(nowMs + 4_000_000);
      await act(async () => {
        window.dispatchEvent(new Event("focus"));
      });

      await waitFor(() => {
        expect(store.getState().user.access).toBe("");
      });
      expect(screen.getByText("Sign In")).toBeInTheDocument();
    } finally {
      dateSpy.mockRestore();
    }
  });
});
