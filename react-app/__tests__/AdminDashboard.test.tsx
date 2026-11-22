// @vitest-environment jsdom
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import "@testing-library/jest-dom";
import { http } from "msw";
import { describe, expect, it } from "vitest";
import { AdminRoute } from "../src/components/AdminRoute";
import { AdminDashboard } from "../src/pages/AdminDashboard";
import { server } from "./mocks/server";
import { renderWithProviders } from "./testutils";

const djangoBaseUrl = (import.meta.env.VITE_APP_DJANGO_URL || "").endsWith("/")
  ? import.meta.env.VITE_APP_DJANGO_URL
  : `${import.meta.env.VITE_APP_DJANGO_URL}/`;

const signedInState = {
  user: {
    access: "valid-jwt-token",
    refresh: "valid-refresh-token",
    username: "spike",
    darkMode: false,
  },
};

describe("AdminRoute guard", () => {
  it("prompts anonymous visitors to sign in", () => {
    renderWithProviders(
      <AdminRoute>
        <AdminDashboard />
      </AdminRoute>,
    );

    expect(screen.getByText("Sign in required")).toBeInTheDocument();
    expect(
      screen.queryByText("Admin Monitoring Dashboard"),
    ).not.toBeInTheDocument();
  });

  it("shows ErrorPage when signed-in user is not staff", async () => {
    server.use(
      http.get(`${djangoBaseUrl}users/api/me/`, () => {
        return Response.json(
          {
            id: 2,
            username: "regularuser",
            email: "user@example.com",
            is_staff: false,
          },
          { status: 200 },
        );
      }),
    );

    renderWithProviders(
      <AdminRoute>
        <AdminDashboard />
      </AdminRoute>,
      { preloadedState: signedInState },
    );

    expect(await screen.findByText("Error!")).toBeInTheDocument();
    expect(
      screen.queryByText("Admin Monitoring Dashboard"),
    ).not.toBeInTheDocument();
  });

  it("renders dashboard for verified staff users", async () => {
    renderWithProviders(
      <AdminRoute>
        <AdminDashboard />
      </AdminRoute>,
      { preloadedState: signedInState },
    );

    expect(
      await screen.findByText("Admin Monitoring Dashboard"),
    ).toBeInTheDocument();
    expect(await screen.findByText("IEX HIST Daily Load")).toBeInTheDocument();
    expect(await screen.findByText("HEALTHY")).toBeInTheDocument();
    expect(await screen.findByText("FAILED")).toBeInTheDocument();
    expect(await screen.findByText("1450")).toBeInTheDocument();
    expect(
      await screen.findByText("/django/chatbot/api/conversations/"),
    ).toBeInTheDocument();
    expect(await screen.findByText("58.2%")).toBeInTheDocument();
  });

  it("separates API client errors from other 4xx traffic", async () => {
    renderWithProviders(
      <AdminRoute>
        <AdminDashboard />
      </AdminRoute>,
      { preloadedState: signedInState },
    );

    expect(await screen.findByText("API 7 · Other 16")).toBeInTheDocument();
    const unauthorizedCard = (await screen.findByText("401 Unauthorized"))
      .parentElement as HTMLElement;
    expect(within(unauthorizedCard).getByText("4")).toBeInTheDocument();
    const notFoundCard = screen.getByText("404 Not Found")
      .parentElement as HTMLElement;
    expect(within(notFoundCard).getByText("2")).toBeInTheDocument();
    expect(screen.getByText("Top API 4xx Paths")).toBeInTheDocument();
    expect(
      screen.getByText("/django/portfolio/api/assets/"),
    ).toBeInTheDocument();
  });
});

describe("AdminDashboard Logs tab", () => {
  it("switches to Logs tab and filters log events by level and search text", async () => {
    renderWithProviders(<AdminDashboard />, { preloadedState: signedInState });

    const logsTab = await screen.findByRole("tab", { name: "Logs" });
    await userEvent.click(logsTab);

    expect(await screen.findByText(/Sample error event/)).toBeInTheDocument();
    expect(await screen.findByText(/Health check ok/)).toBeInTheDocument();

    // Filter by ERROR level
    const levelSelect = screen.getByLabelText("Level");
    await userEvent.selectOptions(levelSelect, "ERROR");

    await waitFor(() => {
      expect(screen.queryByText(/Health check ok/)).not.toBeInTheDocument();
    });
    expect(screen.getByText(/Sample error event/)).toBeInTheDocument();
  });
});
