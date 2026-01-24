import { http } from "msw";

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
];
