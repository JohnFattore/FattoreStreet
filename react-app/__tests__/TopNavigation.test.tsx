import { describe, it, expect } from "vitest";
import "@testing-library/jest-dom";
import { screen, within } from "@testing-library/react";
import { renderWithRoute } from "./testutils";
import TopNavigation from "../src/components/TopNavigation";

function renderAt(path: string) {
  return renderWithRoute(<TopNavigation />, { path: "*", initialEntry: path });
}

const subNavQuery = { name: "Portfolio" };

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
    expect(
      screen.queryByRole("navigation", subNavQuery),
    ).not.toBeInTheDocument();
  });

  it.each(["/portfolio", "/visualizer", "/watchlist", "/account/3"])(
    "shows the portfolio sub nav and keeps the Portfolio tab active on %s",
    (path) => {
      renderAt(path);
      const subNav = screen.getByRole("navigation", subNavQuery);

      expect(
        within(subNav)
          .getAllByRole("link")
          .map((link) => link.textContent),
      ).toEqual(["Portfolio", "Allocation", "Watchlist"]);

      const [topBar] = screen.getAllByRole("navigation");
      expect(
        within(topBar).getByRole("link", { name: "Portfolio" }),
      ).toHaveClass("active");
    },
  );

  it.each(["/portfolios", "/watchlist-archive", "/accounts"])(
    "does not treat look-alike path %s as part of the portfolio group",
    (path) => {
      renderAt(path);

      expect(
        screen.queryByRole("navigation", subNavQuery),
      ).not.toBeInTheDocument();
      const [topBar] = screen.getAllByRole("navigation");
      expect(
        within(topBar).getByRole("link", { name: "Portfolio" }),
      ).not.toHaveClass("active");
    },
  );

  it("marks the current sub nav page active", () => {
    renderAt("/visualizer");
    const subNav = screen.getByRole("navigation", subNavQuery);

    expect(within(subNav).getByText("Allocation")).toHaveClass("active");
    expect(within(subNav).getByText("Watchlist")).not.toHaveClass("active");
  });

  it("has a security search box on every page", () => {
    renderAt("/blog");

    expect(
      screen.getByRole("search").querySelector('[role="combobox"]'),
    ).toHaveAccessibleName("Search securities");
  });
});
