import { http } from 'msw';

export const handlers = [
  http.get(import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("assets/"), () => {
    return Response.json([{
      "id": 65,
      "shares": "1.00000",
      "cost_basis": "239.67",
      "sell_price": null,
      "buy_date": "2021-06-02",
      "sell_date": null,
      "user": 1,
      "asset_info": {
          "id": 25,
          "ticker": "MSFT",
          "short_name": "Microsoft Corporation",
          "long_name": "Microsoft Corporation",
          "type": "EQUITY",
          "exchange": "NASDAQ",
          "market": "us_market"
      },
      "snp500_buy_date": {
          "id": 609,
          "date": "2021-06-02",
          "price": "398.28"
      },
      "snp500_sell_date": null
  }], { status: 200 });
  }),

  http.get(import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("quote/"), () => {
    return Response.json({
      "c": 536.7,
      "d": -27.82,
      "dp": -4.9281,
      "h": 547.97,
      "l": 536.7,
      "o": 545.11,
      "pc": 564.52,
      "t": 1743710400
  }, { status: 200 });
  }),

  http.post(import.meta.env.VITE_APP_DJANGO_USERS_URL.concat("token/"), () => {
    return Response.json({
      "refresh": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0b2tlbl90eXBlIjoicmVmcmVzaCIsImV4cCI6MTc0MzcyODI2MywiaWF0IjoxNzQzNjQxODYzLCJqdGkiOiJjMzdhYTgwN2EzN2U0NzM1YmEwNjg3ZTU2NTdlOTgwZCIsInVzZXJfaWQiOjR9.0lnm8JXjuQA1g39Sd390WCi7gvLuY-kDeNOdcLcIQzw",
      "access": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0b2tlbl90eXBlIjoiYWNjZXNzIiwiZXhwIjoxNzQzNjQyMTYzLCJpYXQiOjE3NDM2NDE4NjMsImp0aSI6IjE0Nzc4MDJkNjc4YTQ2OTM5MTMzNmQzMjIzODYxYmFjIiwidXNlcl9pZCI6NH0.1scYah9rH9XcikArJz64MC38VKWIzx0aWv4SdJClxEw"
  }, { status: 200 });
  }),

  http.get(import.meta.env.VITE_APP_DJANGO_RESTAURANTS_URL.concat("review-list/"), () => {
    return Response.json([{
      "user": 2,
      "rating": "3.0",
      "comment": "ice cream",
      "id": 17,
      "restaurant": 104622,
      "restaurant_detail": {
          "yelp_id": "oaboaRBUgGjbo2kfUIKDLQ",
          "name": "Mike's Ice Cream",
          "address": "129 2nd Ave N",
          "state": "TN",
          "city": "Nashville",
          "latitude": "36.16264920",
          "longitude": "-86.77597330",
          "categories": "Ice Cream & Frozen Yogurt, Coffee & Tea, Restaurants, Sandwiches, Food",
          "stars": 4.5,
          "review_count": 593,
          "id": 104622
      }}], { status: 200 });
  }),

http.get(import.meta.env.VITE_APP_DJANGO_RESTAURANTS_URL.concat("restaurant-list-create/"), () => {
  return Response.json([{
    "yelp_id": "bBDDEgkFA1Otx9Lfe7BZUQ",
    "name": "Sonic Drive-In",
    "address": "2312 Dickerson Pike",
    "state": "TN",
    "city": "Nashville",
    "latitude": "36.20810240",
    "longitude": "-86.76816960",
    "categories": "Ice Cream & Frozen Yogurt, Fast Food, Burgers, Restaurants, Food",
    "stars": 1.5,
    "review_count": 10,
    "id": 104585
},], { status: 200 });
}),
];