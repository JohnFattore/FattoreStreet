import { createApi, BaseQueryFn } from "@reduxjs/toolkit/query/react";
import axios, { AxiosRequestConfig, AxiosError } from "axios";
import { IAsset, IEquityInfo, IETFInfo } from "../interfaces";
import { RootState } from "../main";

const axiosBaseQuery =
  (): BaseQueryFn<{
    url: string;
    method: AxiosRequestConfig["method"];
    data?: AxiosRequestConfig["data"];
    params?: AxiosRequestConfig["params"];
  }> =>
  async ({ url, method, data, params }, api) => {
    try {
      const state = api.getState() as RootState;
      const access = state.user.access;
      const result = await axios({
        url: import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL + url,
        method,
        data,
        params: params,
        headers: access ? { Authorization: `Bearer ${access}` } : undefined,
      });
      return { data: result.data };
    } catch (axiosError) {
      const err = axiosError as AxiosError;
      return {
        error: {
          status: err.response?.status,
          data: err.response?.data || err.message,
        },
      };
    }
  };

export const api = createApi({
  reducerPath: "api",
  baseQuery: axiosBaseQuery(),
  tagTypes: ["Assets"],
  endpoints: (builder) => ({
    getAssets: builder.query<IAsset[], void>({
      query: () => ({
        url: "assets/",
        method: "GET",
      }),
      transformResponse: (response: any[]): IAsset[] => {
        return response.map((item) => ({
          id: item.id,
          ticker: item.ticker,
          shares: Number(item.shares),
          buyDate: item.buy_date,
          buyPrice: item.buy_price,
          snp500PriceBuy: item.buy_SnP500,
          sellDate: item.sell_date,
          sellPrice: item.sell_price,
          snp500PriceSell: item.sell_SnP500,
        }));
      },
      providesTags: [{ type: "Assets", id: "LIST" }],
    }),
    postNewAsset: builder.mutation({
      query: (newAsset) => ({
        url: "assets/",
        method: "POST",
        data: newAsset,
      }),
      invalidatesTags: [{ type: "Assets", id: "LIST" }],
    }),
    getAsset: builder.query<IAsset, number>({
      query: (id) => ({
        url: `assets/${id}`,
        method: "GET",
      }),
      transformResponse: (response: any): IAsset => {
        return {
          id: response.id,
          ticker: response.ticker,
          shares: Number(response.shares),
          buyDate: response.buy_date,
          buyPrice: response.buy_price,
          snp500PriceBuy: response.buy_SnP500,
          sellDate: response.sell_date,
          sellPrice: response.sell_price,
          snp500PriceSell: response.sell_SnP500,
        };
      },
      providesTags: [],
    }),
    deleteAsset: builder.mutation<void, number>({
      query: (id) => ({
        url: `assets/${id}/`,
        method: "DELETE",
      }),
      invalidatesTags: [{ type: "Assets", id: "LIST" }],
    }),
    patchAsset: builder.mutation({
      query: ({ id, ...patch }) => ({
        url: `assets/${id}/`,
        method: "PATCH",
        data: patch,
      }),
      invalidatesTags: [{ type: "Assets", id: "LIST" }],
    }),
    getAssetInfos: builder.query<
      Record<string, IEquityInfo | IETFInfo>,
      string[]
    >({
      query: (tickers) => ({
        url: "asset-info/",
        method: "GET",
        params: { tickers: tickers.join(",") },
      }),
      transformResponse: (
        response: any
      ): Record<string, IEquityInfo | IETFInfo> => {
        const items = Array.isArray(response) ? response : response.data; // handle only the non errored tickers
        const result: Record<string, IEquityInfo | IETFInfo> = {};
        items.forEach((item) => {
          if (item.type === "EQUITY") {
            result[item.ticker] = {
              ticker: item.ticker,
              shortName: item.short_name,
              longName: item.long_name,
              type: "EQUITY",
              exchange: item.exchange,
              market: item.market,
              currentPrice: item.current_price,
              percentChangeDaily: item.percent_change_daily,
              percentChangeWeekly: item.percent_change_weekly,
              percentChangeMonthly: item.percent_change_monthly,
              percentChangeYTD: item.percent_change_YTD,
              percentChangeYearly: item.percent_change_yearly,
              percentChange3Years: item.percent_change_3_years,
              percentChange5Years: item.percent_change_5_years,
              dividendYield: item.dividend_yield,
              marketCap: item.market_cap,
              trailingPE: item.market_cap / item.net_income,
              incomeTTM: item.net_income,
              revenueTTM: item.total_revenue,
              netMarginTTM: item.net_income / item.total_revenue,
            };
          } else if (item.type === "ETF" || item.type === "MUTUALFUND") {
            result[item.ticker] = {
              ticker: item.ticker,
              shortName: item.short_name,
              longName: item.long_name,
              type: item.type,
              exchange: item.exchange,
              market: item.market,
              currentPrice: item.current_price,
              percentChangeDaily: item.percent_change_daily,
              percentChangeWeekly: item.percent_change_weekly,
              percentChangeMonthly: item.percent_change_monthly,
              percentChangeYTD: item.percent_change_YTD,
              percentChangeYearly: item.percent_change_yearly,
              percentChange3Years: item.percent_change_3_years,
              percentChange5Years: item.percent_change_5_years,
              marketCap: item.market_cap,
              trailingPE: item.ttm_pe,
              expenseRatio: item.expenseRatio,
            };
          } else {
            throw new Error(
              `Unknown type '${item.type}' in asset-info response`
            );
          }
        });
        return result;
      },
    }),
    getAssetPrices: builder.query<
      any,
      string
    >({
      query: (ticker) => ({
        url: "asset-prices/",
        method: "GET",
        params: { ticker: ticker },
      }),
    }),
    getFredData: builder.query<
      any,
      { series_id: string; compute_yoy?: boolean }[]
    >({
      query: (seriesList) => ({
        url: "fred-data/",
        method: "POST",
        data: seriesList,
      }),
    }),
    getQuote: builder.query<
      any,
      string
    >({
      query: (ticker) => ({
        url: "quote/",
        method: "GET",
        params: { symbol: ticker },
      }),
    }),
  }),
});

export const {
  useGetAssetInfosQuery,
  useGetAssetPricesQuery,
  useGetAssetsQuery,
  usePostNewAssetMutation,
  useGetAssetQuery,
  useDeleteAssetMutation,
  usePatchAssetMutation,
  useGetFredDataQuery,
  useGetQuoteQuery
} = api;
