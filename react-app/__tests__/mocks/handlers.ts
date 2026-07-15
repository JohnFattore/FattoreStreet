import { http } from "msw";

const djangoBaseUrl = (import.meta.env.VITE_APP_DJANGO_URL || "").endsWith("/")
  ? import.meta.env.VITE_APP_DJANGO_URL
  : `${import.meta.env.VITE_APP_DJANGO_URL}/`;

const usersApiBaseUrl = djangoBaseUrl + "users/api/";
const portfolioApiBaseUrl = djangoBaseUrl + "portfolio/api/";
const restaurantsApiBaseUrl = djangoBaseUrl + "restaurants/api/";
const changeflowApiBaseUrl = djangoBaseUrl + "changeflow/api/";
const blogApiBaseUrl = djangoBaseUrl + "blog/api/";
const chatbotApiBaseUrl = djangoBaseUrl + "chatbot/api/";

const escapeRegExp = (value: string) =>
  value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

export const handlers = [
  http.get(
    portfolioApiBaseUrl.concat("accounts/"),
    () => {
      return Response.json(
        [
          { id: 1, name: "Taxable Brokerage", account_type: "TAXABLE_ACCOUNT" },
          { id: 2, name: "Roth IRA", account_type: "ROTH_IRA" },
        ],
        { status: 200 }
      );
    }
  ),

  http.get(
    portfolioApiBaseUrl.concat("assets/"),
    () => {
      return Response.json(
        [
          {
            id: 223,
            ticker: "MSFT",
            shares: "10.00000",
            buy_date: "2025-07-30",
            buy_price: 300,
            buy_SnP500: 400,
            sell_date: null,
            sell_price: null,
            sell_SnP500: null,
            user: 5,
            account: 1,
          },
          {
            id: 224,
            ticker: "AAPL",
            shares: "20.00000",
            buy_date: "2025-07-30",
            buy_price: 150,
            buy_SnP500: 400,
            sell_date: null,
            sell_price: null,
            sell_SnP500: null,
            user: 5,
            account: 2,
          },
        ],
        { status: 200 }
      );
    }
  ),

  http.get(
    portfolioApiBaseUrl.concat("asset-info/"),
    () => {
      return Response.json(
        {
          data: [
            {
              ticker: "MSFT",
              short_name: "Microsoft Corporation",
              long_name: "Microsoft Corporation",
              type: "EQUITY",
              market: "us_market",
              exchange: "NASDAQ",
              current_price: 500,
              percent_change_daily: 0.02, // 2% up
              percent_change_weekly: 0.03,
              percent_change_monthly: 0.04,
              percent_change_YTD: 0.05,
              percent_change_yearly: 0.06,
              percent_change_3_years: 0.2,
              percent_change_5_years: 0.5,
              dividend_yield: 0.008,
              market_cap: 1000000000,
              net_income: 100000000,
              total_revenue: 500000000,
              ttm_pe: 20,
              expenseRatio: 0,
            },
            {
              ticker: "AAPL",
              short_name: "Apple Inc.",
              long_name: "Apple Inc.",
              type: "EQUITY",
              market: "us_market",
              exchange: "NASDAQ",
              current_price: 200,
              percent_change_daily: -0.01, // 1% down
              percent_change_weekly: 0.01,
              percent_change_monthly: 0.02,
              percent_change_YTD: 0.03,
              percent_change_yearly: 0.04,
              percent_change_3_years: 0.15,
              percent_change_5_years: 0.35,
              dividend_yield: 0.006,
              market_cap: 900000000,
              net_income: 120000000,
              total_revenue: 600000000,
              ttm_pe: 18,
              expenseRatio: 0,
            },
            {
              ticker: "VOO",
              short_name: "Vanguard S&P 500 ETF",
              long_name: "Vanguard S&P 500 ETF",
              type: "ETF",
              market: "us_market",
              exchange: "NYSEARCA",
              current_price: 525,
              percent_change_daily: 0.01,
              percent_change_weekly: 0.02,
              percent_change_monthly: 0.03,
              percent_change_YTD: 0.05,
              percent_change_yearly: 0.09,
              percent_change_3_years: 0.28,
              percent_change_5_years: 0.62,
              dividend_yield: 0.014,
              market_cap: 700000000,
              net_income: 0,
              total_revenue: 0,
              ttm_pe: 24,
              expenseRatio: 0.0003,
            },
          ]
        },
        { status: 200 }
      );
    }
  ),

  // --- Spring Boot: indexes ---
  http.get(import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("indexes"), () => {
    return Response.json(
      [
        { code: "FAT100", displayName: "Fattore 100" },
        { code: "FAT1000", displayName: "Fattore 1000" },
        { code: "FAT50", displayName: "Fattore 50" },
      ],
      { status: 200 }
    );
  }),
  http.get(import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("index-members"), ({ request }) => {
    const url = new URL(request.url);
    const code = url.searchParams.get("code") || "";
    if (code !== "FAT50" && code !== "FAT100" && code !== "FAT1000") {
      return Response.json([], { status: 200 });
    }
    const indexLabel =
      code === "FAT100" ? "Fattore 100" : code === "FAT1000" ? "Fattore 1000" : "Fattore 50";
    return Response.json(
      [
        {
          id: 1,
          percent: 5.5,
          index: indexLabel,
          outlier: false,
          notes: "",
          stock: {
            ticker: "AAPL",
            name: "Apple",
            marketCap: 1000000000,
            volume: 1000000,
            volumeUSD: 200000000,
            freeFloat: 0.9,
            freeFloatMarketCap: 900000000,
            countryIncorp: "United States",
            countryHQ: "United States",
            stateIncorp: "DE",
            stateHQ: "CA",
            securityType: "Common Stock",
            yearIPO: 1980,
          },
        },
      ],
      { status: 200 }
    );
  }),

  http.get(import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("iwb-reference-holdings"), () => {
    return Response.json(
      [
        { ticker: "AAPL", weightPercent: 5.0 },
        { ticker: "MSFT", weightPercent: 4.25 },
      ],
      { status: 200 }
    );
  }),

  http.get(
    portfolioApiBaseUrl.concat("quote/"),
    () => {
      return Response.json(
        {
          price: 526,
          percent_change_daily: 1.05
        },
        { status: 200 }
      );
    }
  ),

  http.post(
    import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("fred-data"),
    () => {
      return Response.json(
        {
          DGS10: [
            {
              date: "1962-01-02",
              value: 4.06,
            },
            {
              date: "1962-01-03",
              value: 4.03,
            },
            {
              date: "1962-01-04",
              value: 3.99,
            },
            {
              date: "1962-01-05",
              value: 4.02,
            },
          ],
          UNRATE: [
            {
              date: "1962-01-02",
              value: 4.06,
            },
            {
              date: "1962-01-03",
              value: 4.03,
            },
            {
              date: "1962-01-04",
              value: 3.99,
            },
            {
              date: "1962-01-05",
              value: 4.02,
            },
          ],
          CPIAUCSL: [
            {
              date: "1962-01-02",
              value: 4.06,
            },
            {
              date: "1962-01-03",
              value: 4.03,
            },
            {
              date: "1962-01-04",
              value: 3.99,
            },
            {
              date: "1962-01-05",
              value: 4.02,
            },
          ],
          DTWEXBGS: [
            {
              date: "1962-01-02",
              value: 4.06,
            },
            {
              date: "1962-01-03",
              value: 4.03,
            },
            {
              date: "1962-01-04",
              value: 3.99,
            },
            {
              date: "1962-01-05",
              value: 4.02,
            },
          ],
          FEDFUNDS: [
            {
              date: "1962-01-02",
              value: 4.06,
            },
            {
              date: "1962-01-03",
              value: 4.03,
            },
            {
              date: "1962-01-04",
              value: 3.99,
            },
            {
              date: "1962-01-05",
              value: 4.02,
            },
          ],
          GDP: [
            {
              date: "1962-01-02",
              value: 4.06,
            },
            {
              date: "1962-01-03",
              value: 4.03,
            },
            {
              date: "1962-01-04",
              value: 3.99,
            },
            {
              date: "1962-01-05",
              value: 4.02,
            },
          ],
        },
        { status: 200 }
      );
    }
  ),

  http.post(usersApiBaseUrl.concat("token/"), () => {
    return Response.json(
      {
        refresh:
          "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0b2tlbl90eXBlIjoicmVmcmVzaCIsImV4cCI6MTc0MzcyODI2MywiaWF0IjoxNzQzNjQxODYzLCJqdGkiOiJjMzdhYTgwN2EzN2U0NzM1YmEwNjg3ZTU2NTdlOTgwZCIsInVzZXJfaWQiOjR9.0lnm8JXjuQA1g39Sd390WCi7gvLuY-kDeNOdcLcIQzw",
        access:
          "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0b2tlbl90eXBlIjoiYWNjZXNzIiwiZXhwIjoxNzQzNjQyMTYzLCJpYXQiOjE3NDM2NDE4NjMsImp0aSI6IjE0Nzc4MDJkNjc4YTQ2OTM5MTMzNmQzMjIzODYxYmFjIiwidXNlcl9pZCI6NH0.1scYah9rH9XcikArJz64MC38VKWIzx0aWv4SdJClxEw",
      },
      { status: 200 }
    );
  }),

  http.get(
    restaurantsApiBaseUrl.concat("review-list/"),
    () => {
      return Response.json(
        [
          {
            user: 2,
            rating: "3.0",
            comment: "ice cream",
            id: 17,
            restaurant: 104622,
            restaurant_detail: {
              yelp_id: "oaboaRBUgGjbo2kfUIKDLQ",
              name: "Mike's Ice Cream",
              address: "129 2nd Ave N",
              state: "TN",
              city: "Nashville",
              latitude: "36.16264920",
              longitude: "-86.77597330",
              categories:
                "Ice Cream & Frozen Yogurt, Coffee & Tea, Restaurants, Sandwiches, Food",
              stars: 4.5,
              review_count: 593,
              id: 104622,
            },
          },
        ],
        { status: 200 }
      );
    }
  ),

  http.post(usersApiBaseUrl.concat("token/refresh/"), () => {
    return Response.json(
      {
        access:
          "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0b2tlbl90eXBlIjoiYWNjZXNzIiwiZXhwIjoxNzQzNjQyMTYzLCJpYXQiOjE3NDM2NDE4NjMsImp0aSI6IjE0Nzc4MDJkNjc4YTQ2OTM5MTMzNmQzMjIzODYxYmFjIiwidXNlcl9pZCI6NH0.refreshed",
      },
      { status: 200 }
    );
  }),

  http.post(restaurantsApiBaseUrl.concat("review-create/"), async ({ request }) => {
    const body = (await request.json()) as {
      restaurant?: number;
      rating?: number;
      comment?: string;
    };
    return Response.json(
      {
        user: 2,
        rating: String(body.rating ?? 5),
        comment: body.comment ?? "",
        id: 18,
        restaurant: body.restaurant ?? 104585,
        restaurant_detail: {
          yelp_id: "bBDDEgkFA1Otx9Lfe7BZUQ",
          name: "Sonic Drive-In",
          address: "2312 Dickerson Pike",
          state: "TN",
          city: "Nashville",
          latitude: "36.20810240",
          longitude: "-86.76816960",
          categories:
            "Ice Cream & Frozen Yogurt, Fast Food, Burgers, Restaurants, Food",
          stars: 1.5,
          review_count: 10,
          id: 104585,
        },
      },
      { status: 201 }
    );
  }),

  http.delete(
    new RegExp(escapeRegExp(restaurantsApiBaseUrl) + "review/\\d+/"),
    () => {
      return new Response(null, { status: 204 });
    }
  ),

  http.get(restaurantsApiBaseUrl.concat("restaurant-recommend/"), () => {
    return Response.json(
      [
        {
          yelp_id: "recommend123",
          name: "Prince's Hot Chicken",
          address: "5814 Nolensville Pike",
          state: "TN",
          city: "Nashville",
          latitude: "36.06550000",
          longitude: "-86.71890000",
          categories: "Southern, Chicken Shop, Restaurants",
          stars: 4.5,
          review_count: 1200,
          id: 104700,
        },
      ],
      { status: 200 }
    );
  }),

  // --- Chatbot (Django) ---

  http.get(chatbotApiBaseUrl.concat("chatbot/"), () => {
    return Response.json(
      [
        {
          input_text: "Should I buy index funds?",
          output_text: "**Yes** — low-cost index funds are a great core holding.",
          timestamp: "2026-03-01T00:00:00Z",
        },
      ],
      { status: 200 }
    );
  }),

  http.post(chatbotApiBaseUrl.concat("chatbot/"), () => {
    return Response.json(
      { message: "Stay the course and keep costs low." },
      { status: 200 }
    );
  }),

  http.get(
    restaurantsApiBaseUrl.concat(
      "restaurant-list-create/"
    ),
    () => {
      return Response.json(
        [
          {
            yelp_id: "bBDDEgkFA1Otx9Lfe7BZUQ",
            name: "Sonic Drive-In",
            address: "2312 Dickerson Pike",
            state: "TN",
            city: "Nashville",
            latitude: "36.20810240",
            longitude: "-86.76816960",
            categories:
              "Ice Cream & Frozen Yogurt, Fast Food, Burgers, Restaurants, Food",
            stars: 1.5,
            review_count: 10,
            id: 104585,
          },
        ],
        { status: 200 }
      );
    }
  ),

  // --- Spring Boot admin jobs (text responses echoing the query string) ---

  http.get(import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("admin/asset-load"), ({ request }) => {
    return new Response("asset-load ok" + new URL(request.url).search, { status: 200 });
  }),

  http.get(import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("admin/load"), () => {
    return new Response("legacy load ok", { status: 200 });
  }),

  http.get(import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("admin/sync-frames"), () => {
    return new Response("sync-frames ok", { status: 200 });
  }),

  http.get(import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("admin/load-hist"), ({ request }) => {
    return new Response("load-hist ok" + new URL(request.url).search, { status: 200 });
  }),

  http.get(import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("admin/adjust-prices"), ({ request }) => {
    return new Response("adjust-prices ok" + new URL(request.url).search, { status: 200 });
  }),

  http.get(import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("admin/summarize-filings"), ({ request }) => {
    return new Response("summarize-filings ok" + new URL(request.url).search, { status: 200 });
  }),

  http.post(import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("admin/indexes/refresh-stocks"), ({ request }) => {
    return new Response("refresh-stocks ok" + new URL(request.url).search, { status: 200 });
  }),

  http.post(import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("admin/indexes/rebuild"), ({ request }) => {
    return new Response("rebuild ok" + new URL(request.url).search, { status: 200 });
  }),

  // --- Spring Boot SEC EDGAR ---

  http.get(
    import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("company-fact-sheet"),
    () => {
      return Response.json(
        {
          ticker: "AAPL",
          cik: "0000320193",
          ttmNetIncome: "93736000000",
          ttmRevenue: "383285000000",
          ttmOperatingCashFlow: "110543000000",
          ttmOperatingIncome: "114301000000",
          ttmGrossProfit: "170782000000",
          ttmNetIncomeYoY: "8.25%",
          ttmRevenueYoY: "4.87%",
          latestAssets: "364980000000",
          latestLiabilities: "308030000000",
          latestEquity: "56950000000",
          latestInventory: "7286000000",
          latestCash: "29943000000",
          latestEps: "6.08",
          netMargin: "24.46%",
          grossMargin: "44.56%",
          debtToAssets: "84.39%",
          cashToLiabilities: "9.72%",
          roA: "25.68%",
          ocfToNetIncome: "117.93%",
          latestQuarterEnd: "2024-12-28",
        },
        { status: 200 }
      );
    }
  ),

  http.get(
    import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("quarters"),
    () => {
      return Response.json(
        {
          ticker: "AAPL",
          cik: "0000320193",
          quarters: [
            {
              year: 2024,
              quarter: "Q4",
              periodStart: "2024-09-29",
              periodEnd: "2024-12-28",
              revenues: 124300000000,
              netIncomeLoss: 36330000000,
              operatingIncomeLoss: 42832000000,
              grossProfit: 58274000000,
              epsBasic: 2.41,
              epsDiluted: 2.40,
              assets: 364980000000,
              liabilities: 308030000000,
              equity: 56950000000,
              cash: 29943000000,
              receivables: 66243000000,
              inventory: 7286000000,
              ocf: 29943000000,
              dividends: 3800000000,
              buybacks: 25000000000,
            },
          ],
        },
        { status: 200 }
      );
    }
  ),

  // --- IEX Prices (Spring Boot) ---

  http.get(
    import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("prices"),
    () => {
      return Response.json(
        {
          ticker: "AAPL",
          prices: [
            { date: "2025-03-15", open: 172.5, high: 174.2, low: 171.8, close: 173.9, adjustedOpen: 171.95, adjustedHigh: 173.65, adjustedLow: 171.25, adjustedClose: 173.35, volume: 45230 },
            { date: "2025-03-14", open: 170.0, high: 172.0, low: 169.5, close: 171.5, adjustedOpen: 169.45, adjustedHigh: 171.45, adjustedLow: 168.95, adjustedClose: 170.95, volume: 38100 },
            { date: "2025-03-13", open: 168.0, high: 170.5, low: 167.0, close: 170.0, adjustedOpen: 167.45, adjustedHigh: 169.95, adjustedLow: 166.95, adjustedClose: 169.45, volume: 42500 },
          ],
        },
        { status: 200 }
      );
    }
  ),

  http.get(
    import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("dividends"),
    () => {
      return Response.json(
        {
          ticker: "AAPL",
          dividends: [
            { date: "2025-02-10", value: 0.25 },
            { date: "2025-05-12", value: 0.26 },
            { date: "2025-08-11", value: 0.26 },
          ],
        },
        { status: 200 }
      );
    }
  ),

  http.get(
    import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("splits"),
    () => {
      return Response.json(
        {
          ticker: "AAPL",
          splits: [
            { date: "2014-06-09", ratio: 0.1428571429, value: 0.1428571429 },
            { date: "2020-08-31", ratio: 0.25, value: 0.25 },
          ],
        },
        { status: 200 }
      );
    }
  ),

  // --- Filing Summaries (Spring Boot) ---

  http.get(
    import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("filing-summaries"),
    () => {
      return Response.json(
        {
          ticker: "AAPL",
          summaries: [
            {
              filingDate: "2024-11-01",
              accessionNumber: "0000320193-24-000123",
              summary: "Apple reported strong revenue growth driven by iPhone and Services segments. Management highlighted continued investment in AI and machine learning capabilities across the product lineup.",
            },
            {
              filingDate: "2023-11-03",
              accessionNumber: "0000320193-23-000108",
              summary: "Revenue declined slightly year-over-year due to macroeconomic headwinds. Services revenue reached an all-time high, partially offsetting hardware weakness.",
            },
          ],
        },
        { status: 200 }
      );
    }
  ),

  // --- Account detail ---

  http.get(
    new RegExp(
      portfolioApiBaseUrl.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") +
        "accounts/\\d+/"
    ),
    () => {
      return Response.json(
        { id: 1, name: "Taxable Brokerage", account_type: "TAXABLE_ACCOUNT" },
        { status: 200 }
      );
    }
  ),

  // --- Asset prices ---

  http.get(
    portfolioApiBaseUrl.concat("asset-prices/"),
    () => {
      return Response.json(
        [
          { date: "2025-03-15", value: 174.0 },
          { date: "2025-03-14", value: 171.0 },
          { date: "2025-03-13", value: 169.5 },
        ],
        { status: 200 }
      );
    }
  ),

  http.get(
    portfolioApiBaseUrl.concat("asset-dividends/"),
    () => {
      return Response.json(
        [
          { date: "2025-02-10", value: 0.25 },
          { date: "2025-05-12", value: 0.26 },
          { date: "2025-08-08", value: 0.26 },
        ],
        { status: 200 }
      );
    }
  ),

  http.get(
    portfolioApiBaseUrl.concat("asset-splits/"),
    () => {
      return Response.json(
        [
          { date: "2014-06-09", value: 7 },
          { date: "2020-08-31", value: 4 },
        ],
        { status: 200 }
      );
    }
  ),

  // --- Quarterly data (Django / yfinance) ---

  http.get(
    portfolioApiBaseUrl.concat("quarterly-data/"),
    () => {
      return Response.json(
        [
          {
            year: 2024,
            quarter: 4,
            periodEnd: "2024-12-28",
            revenues: 124300000000,
            netIncomeLoss: 36330000000,
            operatingIncomeLoss: 42832000000,
            grossProfit: 58274000000,
            earningsPerShareBasic: 2.41,
            earningsPerShareDiluted: 2.40,
            assets: 364980000000,
            liabilities: 308030000000,
            stockholdersEquity: 56950000000,
            cashAndCashEquivalentsAtCarryingValue: 29943000000,
            accountsReceivableNetCurrent: 66243000000,
            inventoryNet: 7286000000,
            netCashProvidedByUsedInOperatingActivities: 29943000000,
            paymentsOfDividends: 3800000000,
            paymentsForRepurchaseOfCommonStock: 25000000000,
          },
        ],
        { status: 200 }
      );
    }
  ),

  // --- Mutations ---

  http.post(
    usersApiBaseUrl.concat("users/"),
    () => {
      return Response.json(
        { id: 10, username: "newuser", email: "new@test.com" },
        { status: 201 }
      );
    }
  ),

  http.post(
    portfolioApiBaseUrl.concat("accounts/"),
    () => {
      return Response.json(
        { id: 3, name: "New Account", account_type: "ROTH_IRA" },
        { status: 201 }
      );
    }
  ),

  http.post(
    portfolioApiBaseUrl.concat("assets/"),
    () => {
      return Response.json(
        {
          id: 300,
          ticker: "VTI",
          shares: "5.00000",
          buy_date: "2024-01-15",
          buy_price: 1100,
          buy_SnP500: 480,
          sell_date: null,
          sell_price: null,
          sell_SnP500: null,
          user: 5,
          account: 1,
        },
        { status: 201 }
      );
    }
  ),
  http.post(
    changeflowApiBaseUrl.concat("tickets/"),
    async ({ request }) => {
      const body = (await request.json()) as { title?: string; description?: string };
      return Response.json(
        {
          id: 500,
          title: body.title ?? "",
          description: body.description ?? "",
          status: "OPEN",
          user: 5,
          created_at: "2026-02-27T00:00:00Z",
          updated_at: "2026-02-27T00:00:00Z",
        },
        { status: 201 }
      );
    }
  ),

  // --- Blog (Django) ---
  http.get(blogApiBaseUrl.concat("posts/"), () => {
    return Response.json(
      {
        count: 1,
        next: null,
        previous: null,
        results: [
          {
            title: "Hello World",
            slug: "hello-world",
            excerpt: "Intro",
            cover_image_url: "",
            published_at: "2026-03-01T00:00:00Z",
            created_at: "2026-03-01T00:00:00Z",
            updated_at: "2026-03-01T00:00:00Z",
            author_username: "spike",
            categories: [{ name: "Investing", slug: "investing" }],
            tags: [{ name: "Bogleheads", slug: "bogleheads" }],
          },
        ],
      },
      { status: 200 }
    );
  }),

  http.get(blogApiBaseUrl.concat("posts/hello-world/"), () => {
    return Response.json(
      {
        title: "Hello World",
        slug: "hello-world",
        excerpt: "Intro",
        body_markdown: "# Hello\n\nThis is a test post.",
        cover_image_url: "",
        published_at: "2026-03-01T00:00:00Z",
        created_at: "2026-03-01T00:00:00Z",
        updated_at: "2026-03-01T00:00:00Z",
        author_username: "spike",
        categories: [{ name: "Investing", slug: "investing" }],
        tags: [{ name: "Bogleheads", slug: "bogleheads" }],
      },
      { status: 200 }
    );
  }),
];
