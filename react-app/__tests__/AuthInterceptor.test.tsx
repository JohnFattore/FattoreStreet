// @vitest-environment jsdom
import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import axios from "axios";
import { http } from "msw";
import App from "../src/App.tsx";
import { renderWithProviders } from "./testutils";
import { server } from "./mocks/server";

// The global axios response interceptor in App.tsx: a 401 on an expired
// access token triggers one silent refresh + retry; a 401 whose detail says
// the token itself is invalid/expired (what SimpleJWT returns from the refresh
// endpoint) logs the user out.

const djangoBaseUrl = (import.meta.env.VITE_APP_DJANGO_URL || "").endsWith("/")
  ? import.meta.env.VITE_APP_DJANGO_URL
  : `${import.meta.env.VITE_APP_DJANGO_URL}/`;
const probeUrl = djangoBaseUrl.concat("interceptor-probe/");
const refreshUrl = djangoBaseUrl.concat("users/api/token/refresh/");

const loggedInUser = {
  user: {
    username: "testUser",
    access: "old-access",
    refresh: "old-refresh",
    darkMode: false,
  },
};

// SimpleJWT's response for an expired access token on a protected view
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

async function renderApp(preloadedState: object = loggedInUser) {
  const view = renderWithProviders(<App />, { preloadedState });
  // Wait for the first render so the interceptor's useEffect has run
  await screen.findByText("Master Your Financial Future");
  return view;
}

describe("App axios 401 interceptor", () => {
  it("refreshes the access token and retries once on an expired access token", async () => {
    let refreshCalls = 0;
    const probeAuthHeaders: (string | null)[] = [];
    server.use(
      http.post(refreshUrl, () => {
        refreshCalls += 1;
        return Response.json({ access: "new-access" });
      }),
      http.get(probeUrl, ({ request }) => {
        const auth = request.headers.get("Authorization");
        probeAuthHeaders.push(auth);
        return auth === "Bearer new-access"
          ? Response.json({ ok: true })
          : Response.json(expiredAccessBody, { status: 401 });
      }),
    );
    const { store } = await renderApp();

    const response = await axios.get(probeUrl, {
      headers: { Authorization: "Bearer old-access" },
    });

    expect(response.data).toEqual({ ok: true });
    expect(refreshCalls).toBe(1);
    expect(probeAuthHeaders).toEqual([
      "Bearer old-access",
      "Bearer new-access",
    ]);
    expect(store.getState().user.access).toBe("new-access");
    expect(store.getState().user.refresh).toBe("old-refresh");
  });

  it("does not refresh a second time when the retried request also 401s", async () => {
    let refreshCalls = 0;
    let probeCalls = 0;
    server.use(
      http.post(refreshUrl, () => {
        refreshCalls += 1;
        return Response.json({ access: "new-access" });
      }),
      http.get(probeUrl, () => {
        probeCalls += 1;
        return Response.json(expiredAccessBody, { status: 401 });
      }),
    );
    await renderApp();

    await expect(axios.get(probeUrl)).rejects.toMatchObject({
      response: { status: 401 },
    });
    expect(refreshCalls).toBe(1);
    expect(probeCalls).toBe(2);
  });

  it("logs out when the refresh token itself is expired", async () => {
    server.use(
      http.post(refreshUrl, () =>
        Response.json(
          { detail: "Token is expired", code: "token_not_valid" },
          { status: 401 },
        ),
      ),
      http.get(probeUrl, () =>
        Response.json(expiredAccessBody, { status: 401 }),
      ),
    );
    const { store } = await renderApp();

    await expect(axios.get(probeUrl)).rejects.toBeDefined();
    expect(store.getState().user.access).toBe("");
    expect(store.getState().user.refresh).toBe("");
    expect(store.getState().user.username).toBe("");
  });

  it("logs out without refreshing when a 401 says the token is invalid", async () => {
    let refreshCalls = 0;
    server.use(
      http.post(refreshUrl, () => {
        refreshCalls += 1;
        return Response.json({ access: "new-access" });
      }),
      http.get(probeUrl, () =>
        Response.json(
          { detail: "Token is invalid", code: "token_not_valid" },
          { status: 401 },
        ),
      ),
    );
    const { store } = await renderApp();

    await expect(axios.get(probeUrl)).rejects.toMatchObject({
      response: { status: 401 },
    });
    expect(refreshCalls).toBe(0);
    expect(store.getState().user.access).toBe("");
    expect(store.getState().user.username).toBe("");
  });

  it("passes the 401 through untouched when there is no refresh token", async () => {
    let refreshCalls = 0;
    server.use(
      http.post(refreshUrl, () => {
        refreshCalls += 1;
        return Response.json({ access: "new-access" });
      }),
      http.get(probeUrl, () =>
        Response.json(expiredAccessBody, { status: 401 }),
      ),
    );
    await renderApp({
      user: { username: "", access: "", refresh: "", darkMode: false },
    });

    await expect(axios.get(probeUrl)).rejects.toMatchObject({
      response: { status: 401 },
    });
    expect(refreshCalls).toBe(0);
  });
});
