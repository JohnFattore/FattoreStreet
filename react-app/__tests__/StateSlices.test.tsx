// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import { http } from "msw";
import userReducer, {
  logout,
  setUserDarkMode,
} from "../src/reducers/userReducer";
import locationReducer, { setLocation } from "../src/reducers/locationReducer";
import watchListReducer, {
  loadTickers,
  removeTicker,
} from "../src/reducers/watchListReducer";
import adminSuccessBarReducer, {
  addTicker,
  clearError,
  removeTicker as removeAdminTicker,
} from "../src/reducers/adminSuccessBarReducer";
import { djangoApi } from "../src/functions/api";
import { createTestStore } from "./testutils";
import { server } from "./mocks/server";

const djangoBaseUrl = (import.meta.env.VITE_APP_DJANGO_URL || "").endsWith("/")
  ? import.meta.env.VITE_APP_DJANGO_URL
  : `${import.meta.env.VITE_APP_DJANGO_URL}/`;

const loggedInUser = {
  username: "testUser",
  access: "old-access",
  refresh: "old-refresh",
  darkMode: true,
};

describe("userReducer actions", () => {
  it("logout clears tokens and username but keeps darkMode", () => {
    const state = userReducer(loggedInUser, logout());
    expect(state.username).toBe("");
    expect(state.access).toBe("");
    expect(state.refresh).toBe("");
    expect(state.darkMode).toBe(true);
  });

  it("setUserDarkMode toggles the flag", () => {
    const state = userReducer(loggedInUser, setUserDarkMode(false));
    expect(state.darkMode).toBe(false);
  });
});

describe("userReducer mutation matchers", () => {
  it("refreshLogin success replaces access and keeps refresh", async () => {
    const store = createTestStore({ user: loggedInUser });

    await store.dispatch(
      djangoApi.endpoints.refreshLogin.initiate("old-refresh"),
    );

    expect(store.getState().user.access).not.toBe("old-access");
    expect(store.getState().user.access).not.toBe("");
    expect(store.getState().user.refresh).toBe("old-refresh");
  });

  it("refreshLogin failure clears both tokens", async () => {
    server.use(
      http.post(djangoBaseUrl.concat("users/api/token/refresh/"), () =>
        Response.json(
          { detail: "Token is blacklisted", code: "token_not_valid" },
          { status: 401 },
        ),
      ),
    );
    const store = createTestStore({ user: loggedInUser });

    await store.dispatch(
      djangoApi.endpoints.refreshLogin.initiate("old-refresh"),
    );

    expect(store.getState().user.access).toBe("");
    expect(store.getState().user.refresh).toBe("");
  });

  it("login failure clears the refresh token", async () => {
    server.use(
      http.post(djangoBaseUrl.concat("users/api/token/"), () =>
        Response.json(
          { detail: "No active account found with the given credentials" },
          { status: 401 },
        ),
      ),
    );
    const store = createTestStore({ user: loggedInUser });

    await store.dispatch(
      djangoApi.endpoints.login.initiate({
        username: "testUser",
        password: "wrong", // pragma: allowlist secret
      }),
    );

    expect(store.getState().user.refresh).toBe("");
  });
});

describe("locationReducer", () => {
  it("setLocation replaces state and city", () => {
    const state = locationReducer(
      undefined,
      setLocation({ state: "CA", city: "San Diego" }),
    );
    expect(state.state).toBe("CA");
    expect(state.city).toBe("San Diego");
  });

  it("defaults to Nashville, TN", () => {
    const state = locationReducer(undefined, { type: "noop" });
    expect(state.state).toBe("TN");
    expect(state.city).toBe("Nashville");
  });
});

describe("watchListReducer", () => {
  it("loadTickers seeds default tickers into empty localStorage", () => {
    const state = watchListReducer(undefined, loadTickers());
    expect(state.tickers).toEqual(["VTI", "SPY"]);
    expect(JSON.parse(localStorage.getItem("tickers") as string)).toEqual([
      "VTI",
      "SPY",
    ]);
  });

  it("loadTickers reads existing tickers from localStorage", () => {
    localStorage.setItem("tickers", JSON.stringify(["MSFT"]));
    const state = watchListReducer(undefined, loadTickers());
    expect(state.tickers).toEqual(["MSFT"]);
  });

  it("removeTicker updates state and localStorage", () => {
    const initial = watchListReducer(undefined, loadTickers());
    const state = watchListReducer(initial, removeTicker("VTI"));
    expect(state.tickers).toEqual(["SPY"]);
    expect(JSON.parse(localStorage.getItem("tickers") as string)).toEqual([
      "SPY",
    ]);
  });
});

describe("adminSuccessBarReducer validation branches", () => {
  it("rejects an empty ticker", () => {
    const state = adminSuccessBarReducer(
      { tickers: [], error: "" },
      addTicker("   "),
    );
    expect(state.tickers).toEqual([]);
    expect(state.error).toBe("Ticker is required.");
  });

  it("rejects an invalid ticker format", () => {
    const state = adminSuccessBarReducer(
      { tickers: [], error: "" },
      addTicker("1BAD$"),
    );
    expect(state.tickers).toEqual([]);
    expect(state.error).toBe("Invalid ticker format.");
  });

  it("removeTicker clears a lingering error", () => {
    const state = adminSuccessBarReducer(
      { tickers: ["AAPL"], error: "Invalid ticker format." },
      removeAdminTicker("AAPL"),
    );
    expect(state.tickers).toEqual([]);
    expect(state.error).toBe("");
  });

  it("clearError resets the error", () => {
    const state = adminSuccessBarReducer(
      { tickers: [], error: "Ticker is required." },
      clearError(),
    );
    expect(state.error).toBe("");
  });
});
