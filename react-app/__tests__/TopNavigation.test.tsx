import { describe, it, expect } from "vitest";
import "@testing-library/jest-dom";
import { render, screen, within } from "@testing-library/react";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { createTestStore } from "./testutils";
import TopNavigation from "../src/components/TopNavigation";

function renderAt(path: string) {
  return render(
    <Provider store={createTestStore()}>
      <MemoryRouter initialEntries={[path]}>
        <TopNavigation />
      </MemoryRouter>
    </Provider>,
  );
}

describe("TopNavigation", () => {
  it("leads with the chatbot and hides restaurants and entertainment", () => {
    renderAt("/");
    const links = within(screen.getByRole("navigation"))
      .getAllByRole("link")
      .map((link) => link.textContent);

    expect(links.slice(0, 4)).toEqual([
      "Fattore Street",
      "Chatbot",
      "Indexes",
      "Economic Indicators",
    ]);
    expect(links).toContain("Portfolio");
    expect(screen.queryByText("Restaurants")).not.toBeInTheDocument();
    expect(screen.queryByText("Entertainment")).not.toBeInTheDocument();
    expect(screen.queryByText("Allocation")).not.toBeInTheDocument();
  });

  it.each(["/portfolio", "/visualizer", "/watchlist", "/account/3"])(
    "shows the portfolio sub nav on %s",
    (path) => {
      renderAt(path);
      const subNav = screen.getByLabelText("Portfolio");

      expect(
        within(subNav)
          .getAllByRole("link")
          .map((link) => link.textContent),
      ).toEqual(["Portfolio", "Allocation", "Watchlist"]);
    },
  );

  it("marks the current sub nav page active", () => {
    renderAt("/visualizer");
    const subNav = screen.getByLabelText("Portfolio");

    expect(within(subNav).getByText("Allocation")).toHaveClass("active");
    expect(within(subNav).getByText("Watchlist")).not.toHaveClass("active");
  });
});
