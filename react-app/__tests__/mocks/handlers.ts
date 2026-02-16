import { http, HttpResponse } from "msw";

export const handlers = [
  http.get(
    import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("accounts/"),
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
    import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("assets/"),
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
    import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("asset-info/"),
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
            },
          ]
        },
        { status: 200 }
      );
    }
  ),

  http.get(
    import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("quote/"),
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
    import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("fred-data/"),
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

  http.post(import.meta.env.VITE_APP_DJANGO_USERS_URL.concat("token/"), () => {
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
    import.meta.env.VITE_APP_DJANGO_RESTAURANTS_URL.concat("review-list/"),
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

  http.get(
    import.meta.env.VITE_APP_DJANGO_RESTAURANTS_URL.concat(
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

  // --- Account detail ---

  http.get(
    new RegExp(
      import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") +
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
    import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("asset-prices/"),
    () => {
      return Response.json(
        [
          { date: "2024-01-02", value: 150.0 },
          { date: "2024-01-03", value: 152.5 },
          { date: "2024-01-04", value: 148.0 },
        ],
        { status: 200 }
      );
    }
  ),

  // --- Quarterly data (Django / yfinance) ---

  http.get(
    import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("quarterly-data/"),
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
    import.meta.env.VITE_APP_DJANGO_USERS_URL.concat("users/"),
    () => {
      return Response.json(
        { id: 10, username: "newuser", email: "new@test.com" },
        { status: 201 }
      );
    }
  ),

  http.post(
    import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("accounts/"),
    () => {
      return Response.json(
        { id: 3, name: "New Account", account_type: "ROTH_IRA" },
        { status: 201 }
      );
    }
  ),

  http.post(
    import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("assets/"),
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
];
