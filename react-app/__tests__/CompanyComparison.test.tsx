// @vitest-environment jsdom
import { screen, waitFor } from "@testing-library/react";
import { expect, describe, it } from "vitest";
import userEvent from "@testing-library/user-event";
import "@testing-library/jest-dom";
import { http } from "msw";
import { CompanyComparison } from "../src/pages/CompanyComparison";
import { pickWinner } from "../src/functions/helperFunctions";
import { renderWithRoute } from "./testutils";
import { toAnalysisFigures } from "../src/functions/analysisFigures";
import { ISECData } from "../src/interfaces";
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

const authenticatedState = {
  user: {
    access: "fake-token",
    refresh: "fake-refresh",
    username: "testuser",
    darkMode: false,
  },
};

const analysisUrl = import.meta.env.VITE_APP_DJANGO_URL.concat(
  "chatbot/api/company-comparison/",
);

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

  it("prompts a signed-out reader to sign in for the analysis", async () => {
    useFactSheetsByTicker();
    let analysisRequested = false;
    server.use(
      http.post(analysisUrl, () => {
        analysisRequested = true;
        return Response.json({ analysis: "x" }, { status: 200 });
      }),
    );
    renderWithRoute(<CompanyComparison />, {
      path: "/compare",
      initialEntry: "/compare?a=AAPL&b=MSFT",
    });

    expect(
      await screen.findByText("AI Comparison Analysis"),
    ).toBeInTheDocument();
    expect(screen.getByText("TTM Income Statement")).toBeInTheDocument();
    expect(analysisRequested).toBe(false);
  });

  it("asks the model nothing until the reader clicks Generate Analysis", async () => {
    useFactSheetsByTicker();
    let analysisRequested = false;
    server.use(
      http.post(analysisUrl, () => {
        analysisRequested = true;
        return Response.json({ analysis: "x" }, { status: 200 });
      }),
    );
    renderWithRoute(<CompanyComparison />, {
      path: "/compare",
      initialEntry: "/compare?a=AAPL&b=MSFT",
      preloadedState: authenticatedState,
    });

    expect(
      await screen.findByRole("button", { name: "Generate Analysis" }),
    ).toBeInTheDocument();
    expect(screen.getByText("AI Analysis")).toBeInTheDocument();
    expect(analysisRequested).toBe(false);
  });

  it("sends every displayed figure and shows the analysis to a signed-in user", async () => {
    useFactSheetsByTicker();
    let sent: { companies: Record<string, string | null>[] } | undefined;
    server.use(
      http.post(analysisUrl, async ({ request }) => {
        sent = (await request.json()) as typeof sent;
        return Response.json(
          { analysis: "**AAPL** is the larger company by revenue." },
          { status: 200 },
        );
      }),
    );
    renderWithRoute(<CompanyComparison />, {
      path: "/compare",
      initialEntry: "/compare?a=AAPL&b=MSFT",
      preloadedState: authenticatedState,
    });

    await userEvent.click(
      await screen.findByRole("button", { name: "Generate Analysis" }),
    );
    const bold = await screen.findByText("AAPL", { selector: "strong" });
    expect(bold).toBeInTheDocument();
    expect(sent?.companies.map((c) => c.ticker)).toEqual(["AAPL", "MSFT"]);
    expect(sent?.companies[0]).toMatchObject({
      ttm_revenue: "383285000000",
      net_margin: "24.46%",
      market_cap: "3500000000000.00",
      latest_quarter_end: "2024-12-28",
    });
    // MSFT is unsized, so its market cap goes as missing rather than a guess
    expect(sent?.companies[1].market_cap).toBeNull();
  });

  it("keeps the comparison when the analysis fails", async () => {
    useFactSheetsByTicker();
    server.use(
      http.post(analysisUrl, () =>
        Response.json(
          {
            detail:
              "The advisor has reached its monthly usage limit and will be back next month.",
          },
          { status: 503 },
        ),
      ),
    );
    renderWithRoute(<CompanyComparison />, {
      path: "/compare",
      initialEntry: "/compare?a=AAPL&b=MSFT",
      preloadedState: authenticatedState,
    });

    await userEvent.click(
      await screen.findByRole("button", { name: "Generate Analysis" }),
    );
    expect(
      await screen.findByText(
        "The advisor has reached its monthly usage limit and will be back next month.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("TTM Income Statement")).toBeInTheDocument();
    expect(screen.getByText("35.96%")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Try Again" }),
    ).toBeInTheDocument();
  });
});

describe("toAnalysisFigures", () => {
  it("maps every figure to its snake_case name, with null for missing", () => {
    const figures = toAnalysisFigures(factSheets.MSFT as unknown as ISECData);
    expect(figures.ticker).toBe("MSFT");
    expect(figures.ro_a).toBeNull();
    expect(figures.ttm_revenue_yoy).toBeNull();
    expect(figures.debt_to_assets).toBe("45.10%");
    expect(Object.keys(figures)).toHaveLength(23);
  });
});
