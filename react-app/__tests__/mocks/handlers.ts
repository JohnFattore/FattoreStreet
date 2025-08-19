import { http } from "msw";

export const handlers = [
  http.get(
    import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("assets"),
    () => {
      return Response.json(
        [
          {
            id: 223,
            ticker: "MSFT",
            shares: "1.00000",
            buy_date: "2025-07-30",
            buy_price: 513.239990234375,
            buy_SnP500: 634.4600219726562,
            sell_date: null,
            sell_price: null,
            sell_SnP500: null,
            user: 5,
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
        [
          {
            ticker: "MSFT",
            short_name: "Microsoft Corporation",
            long_name: "Microsoft Corporation",
            type: "EQUITY",
            market: "us_market",
            exchange: "NASDAQ",
            "1_week_ago": 522.0399780273438,
            "1_month_ago": 510.04998779296875,
            year_to_date: 419.88568115234375,
            "1_year_ago": 416.0345458984375,
            "3_years_ago": 283.5922546386719,
            "5_years_ago": 202.62554931640625,
            market_cap: 3866511802368,
            net_income: 101832000000.0,
            total_revenue: 281724000000.0,
            current_price: 520.17,
            percent_change_daily: -0.004421,
            percent_change_weekly: -0.003582059049212978,
            percent_change_monthly: 0.019841216447865028,
            percent_change_YTD: 0.23883719628741246,
            percent_change_yearly: 0.2503048247512216,
            percent_change_3_years: 0.8342179361095545,
            percent_change_5_years: 1.567149116954338,
          },
        ],
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
