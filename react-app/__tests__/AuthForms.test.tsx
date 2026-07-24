// @vitest-environment jsdom
import { screen, waitFor } from "@testing-library/react";
import { expect, describe, it } from "vitest";
import "@testing-library/jest-dom";
import userEvent from "@testing-library/user-event";
import { http } from "msw";
import LoginForm from "../src/components/LoginForm";
import RegisterForm from "../src/components/RegisterForm";
import WatchListForm from "../src/components/WatchListForm";
import { renderWithProviders } from "./testutils";
import { server } from "./mocks/server";

const djangoBaseUrl = (import.meta.env.VITE_APP_DJANGO_URL || "").endsWith("/")
  ? import.meta.env.VITE_APP_DJANGO_URL
  : `${import.meta.env.VITE_APP_DJANGO_URL}/`;

describe("LoginForm", () => {
  it("stores tokens in the user slice and hides the form on success", async () => {
    const { store } = renderWithProviders(<LoginForm />);

    await userEvent.type(
      screen.getByPlaceholderText("Enter username"),
      "testUser",
    );
    await userEvent.type(
      screen.getByPlaceholderText("Enter password"),
      "hunter2",
    );
    await userEvent.click(screen.getByRole("button", { name: /login/i }));

    await waitFor(() => {
      expect(store.getState().user.access).not.toBe("");
    });
    expect(store.getState().user.username).toBe("testUser");
    expect(store.getState().user.refresh).not.toBe("");
    // LoginForm renders nothing once access is present
    expect(
      screen.queryByPlaceholderText("Enter username"),
    ).not.toBeInTheDocument();
  });

  it("shows the server error when login fails", async () => {
    server.use(
      http.post(djangoBaseUrl.concat("users/api/token/"), () =>
        Response.json(
          { detail: "No active account found with the given credentials" },
          { status: 401 },
        ),
      ),
    );

    renderWithProviders(<LoginForm />);

    await userEvent.type(
      screen.getByPlaceholderText("Enter username"),
      "testUser",
    );
    await userEvent.type(
      screen.getByPlaceholderText("Enter password"),
      "wrong",
    );
    await userEvent.click(screen.getByRole("button", { name: /login/i }));

    expect(
      await screen.findByText(
        "No active account found with the given credentials",
      ),
    ).toBeInTheDocument();
  });
});

describe("RegisterForm", () => {
  const fillForm = async (password2: string) => {
    await userEvent.type(
      screen.getByPlaceholderText("Enter username"),
      "newUser",
    );
    await userEvent.type(
      screen.getByPlaceholderText("Enter email"),
      "new@test.com",
    );
    await userEvent.type(
      screen.getByPlaceholderText("Enter password"),
      "hunter2",
    );
    await userEvent.type(
      screen.getByPlaceholderText("Confirm password"),
      password2,
    );
    await userEvent.click(
      screen.getByRole("button", { name: /register user/i }),
    );
  };

  it("rejects mismatched passwords with a validation error", async () => {
    const { store } = renderWithProviders(<RegisterForm />);

    await fillForm("different");

    expect(await screen.findByRole("password2Error")).toHaveTextContent(
      "Passwords must match",
    );
    expect(store.getState().user.access).toBe("");
  });

  it("registers and logs in on success", async () => {
    const { store } = renderWithProviders(<RegisterForm />);

    await fillForm("hunter2");

    await waitFor(() => {
      expect(store.getState().user.access).not.toBe("");
    });
    expect(store.getState().user.username).toBe("newUser");
    // RegisterForm renders nothing once logged in
    expect(
      screen.queryByPlaceholderText("Enter username"),
    ).not.toBeInTheDocument();
  });

  it("shows the server error when registration fails", async () => {
    server.use(
      http.post(djangoBaseUrl.concat("users/api/users/"), () =>
        Response.json(
          { username: ["A user with that username already exists."] },
          { status: 400 },
        ),
      ),
    );

    const { store } = renderWithProviders(<RegisterForm />);

    await fillForm("hunter2");

    expect(
      await screen.findByText(/A user with that username already exists/),
    ).toBeInTheDocument();
    expect(store.getState().user.access).toBe("");
  });
});

describe("WatchListForm", () => {
  it("adds a ticker after the quote lookup succeeds", async () => {
    const { store } = renderWithProviders(
      <WatchListForm assetInfosLoading={false} />,
    );

    await userEvent.type(
      screen.getByPlaceholderText("Enter Ticker Here"),
      "VTI",
    );
    await userEvent.click(
      screen.getByRole("button", { name: /add to watchlist/i }),
    );

    await waitFor(() => {
      expect(store.getState().watchList.tickers).toContain("VTI");
    });
  });

  it("sets an error when the quote has no price", async () => {
    server.use(
      http.get(djangoBaseUrl.concat("portfolio/api/quote/"), () =>
        Response.json(
          { price: null, percent_change_daily: null },
          { status: 200 },
        ),
      ),
    );

    const { store } = renderWithProviders(
      <WatchListForm assetInfosLoading={false} />,
    );

    await userEvent.type(
      screen.getByPlaceholderText("Enter Ticker Here"),
      "NOPE",
    );
    await userEvent.click(
      screen.getByRole("button", { name: /add to watchlist/i }),
    );

    await waitFor(() => {
      expect(store.getState().watchList.error).toBe(
        "Couldn't retrieve data for ticker",
      );
    });
    expect(store.getState().watchList.tickers).not.toContain("NOPE");
  });
});
