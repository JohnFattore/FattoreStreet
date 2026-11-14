// @vitest-environment jsdom
import { screen, waitFor } from "@testing-library/react";
import { expect, describe, it } from "vitest";
import userEvent from "@testing-library/user-event";
import "@testing-library/jest-dom";
import { http } from "msw";
import { CompanyComparison } from "../src/pages/CompanyComparison";
import { pickWinner } from "../src/functions/helperFunctions";
import { renderWithRoute } from "./testutils";
import { server } from "./mocks/server";

const factSheets: Record<string, Record<string, string>> = {
  AAPL: {
    ticker: "AAPL",
    cik: "0000320193",
    ttmRevenue: "383285000000",
    netMargin: "24.46%",
    debtToAssets: "84.39%",
    latestEps: "6.08",
    latestQuarterEnd: "2024-12-28",
    marketCap: "3500000000000.00",
    marketCapAsOf: "2026-11-13",
  },
  MSFT: {
    ticker: "MSFT",
    cik: "0000789019",
    ttmRevenue: "245122000000",
    netMargin: "35.96%",
    debtToAssets: "45.10%",
    latestEps: "3.30",
    latestQuarterEnd: "2024-12-31",
  },
};

function useFactSheetsByTicker() {
  server.use(
    http.get(
      import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("company-fact-sheet"),
      ({ request }) => {
        const ticker = new URL(request.url).searchParams.get("ticker") ?? "";
        const sheet = factSheets[ticker];
        return sheet
          ? Response.json(sheet, { status: 200 })
          : Response.json({ detail: "Not found" }, { status: 404 });
      },
    ),
  );
}

describe("pickWinner", () => {
  it("picks the higher value when higher is better", () => {
    expect(pickWinner("24.46%", "35.96%", true)).toBe("b");
  });

  it("picks the lower value when lower is better", () => {
    expect(pickWinner("84.39%", "45.10%", false)).toBe("b");
  });

  it("returns null for ties, missing values, or no direction", () => {
    expect(pickWinner("10%", "10%", true)).toBeNull();
    expect(pickWinner(undefined, "10%", true)).toBeNull();
    expect(pickWinner("1", "2", undefined)).toBeNull();
  });
});

describe("CompanyComparison", () => {
  it("shows the ticker form without fetching when no tickers are given", () => {
    renderWithRoute(<CompanyComparison />, {
      path: "/compare",
      initialEntry: "/compare",
    });

    expect(screen.getByText("Compare Companies")).toBeInTheDocument();
    expect(screen.getByLabelText("First ticker")).toBeInTheDocument();
    expect(screen.queryByText("TTM Income Statement")).not.toBeInTheDocument();
  });

  it("renders both companies side by side from the query string", async () => {
    useFactSheetsByTicker();
    renderWithRoute(<CompanyComparison />, {
      path: "/compare",
      initialEntry: "/compare?a=AAPL&b=MSFT",
    });

    expect(await screen.findByText("TTM Income Statement")).toBeInTheDocument();
    expect(screen.getByText("Financial Ratios & Metrics")).toBeInTheDocument();
    expect(screen.getAllByText("AAPL").length).toBeGreaterThan(0);
    expect(screen.getAllByText("MSFT").length).toBeGreaterThan(0);
    expect(screen.getByText("35.96%")).toHaveClass("text-success");
    expect(screen.getByText("24.46%")).not.toHaveClass("text-success");
    expect(screen.getByText("45.10%")).toHaveClass("text-success");
  });

  it("shows market cap with its date, unmarked, and N/A when unsized", async () => {
    useFactSheetsByTicker();
    renderWithRoute(<CompanyComparison />, {
      path: "/compare",
      initialEntry: "/compare?a=AAPL&b=MSFT",
    });

    expect(await screen.findByText("Market Size")).toBeInTheDocument();
    const cap = screen.getByText("$3,500.00 Billion");
    expect(cap).not.toHaveClass("text-success");
    expect(screen.getByText("2026-11-13")).toBeInTheDocument();
    const capRow = screen.getByText("Market Cap").closest("tr");
    expect(capRow).toHaveTextContent("N/A");
  });

  it("normalizes tickers typed straight into the URL", async () => {
    useFactSheetsByTicker();
    renderWithRoute(<CompanyComparison />, {
      path: "/compare",
      initialEntry: "/compare?a=aapl&b=%20msft",
    });

    expect(await screen.findByText("TTM Income Statement")).toBeInTheDocument();
    expect(screen.getByLabelText("First ticker")).toHaveValue("AAPL");
    expect(screen.getByLabelText("Second ticker")).toHaveValue("MSFT");
  });

  it("compares tickers submitted through the form", async () => {
    useFactSheetsByTicker();
    renderWithRoute(<CompanyComparison />, {
      path: "/compare",
      initialEntry: "/compare",
    });

    await userEvent.type(screen.getByLabelText("First ticker"), "aapl");
    await userEvent.type(screen.getByLabelText("Second ticker"), "msft");
    await userEvent.click(screen.getByRole("button", { name: "Compare" }));

    expect(await screen.findByText("TTM Income Statement")).toBeInTheDocument();
    expect(screen.getByText("$6.08")).toBeInTheDocument();
    expect(screen.getByText("$3.30")).toBeInTheDocument();
  });

  it("trims whitespace from submitted tickers", async () => {
    useFactSheetsByTicker();
    renderWithRoute(<CompanyComparison />, {
      path: "/compare",
      initialEntry: "/compare",
    });

    await userEvent.type(screen.getByLabelText("First ticker"), "  aapl ");
    await userEvent.type(screen.getByLabelText("Second ticker"), "msft  ");
    await userEvent.click(screen.getByRole("button", { name: "Compare" }));

    expect(await screen.findByText("TTM Income Statement")).toBeInTheDocument();
    expect(screen.getByLabelText("First ticker")).toHaveValue("AAPL");
    expect(screen.getByLabelText("Second ticker")).toHaveValue("MSFT");
  });

  it("requires both tickers", async () => {
    renderWithRoute(<CompanyComparison />, {
      path: "/compare",
      initialEntry: "/compare",
    });

    await userEvent.type(screen.getByLabelText("First ticker"), "AAPL");
    await userEvent.click(screen.getByRole("button", { name: "Compare" }));

    await waitFor(() => {
      expect(
        screen.getByText("Error: Enter two tickers to compare"),
      ).toBeInTheDocument();
    });
  });

  it("shows an error when a ticker cannot be loaded", async () => {
    useFactSheetsByTicker();
    renderWithRoute(<CompanyComparison />, {
      path: "/compare",
      initialEntry: "/compare?a=AAPL&b=ZZZZ",
    });

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.queryByText("TTM Income Statement")).not.toBeInTheDocument();
  });
});
