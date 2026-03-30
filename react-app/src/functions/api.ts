import { createApi, BaseQueryFn } from "@reduxjs/toolkit/query/react";
import axios, { AxiosRequestConfig, AxiosError } from "axios";
import {
  IAsset,
  IDividendRow,
  IEquityInfo,
  IETFInfo,
  IFilingSummary,
  IIexDividendsResponse,
  IIexPricesResponse,
  IIexSplitsResponse,
  ISECData,
  ISECQuartersResponse,
  ISplitRow,
  IMarketIndex,
  IIndexMemberRow,
  IIwbReferenceHolding,
  IYFinanceQuarter,
} from "../interfaces";
import { RootState } from "../main";

/** Raw snake_case shape returned by Django for a single asset */
interface IRawAsset {
  id: number;
  ticker: string;
  shares: string | number;
  buy_date: string;
  buy_price: string | number | null;
  buy_SnP500: string | number | null;
  sell_date: string | null;
  sell_price: string | number | null;
  sell_SnP500: string | number | null;
  account: number | null;
}

/** Raw snake_case shape returned by Django for asset-info items */
interface IRawAssetInfoItem {
  ticker: string;
  short_name: string;
  long_name: string;
  type: string;
  exchange: string;
  market: string;
  current_price: number;
  percent_change_daily: number;
  percent_change_weekly: number;
  percent_change_monthly: number;
  percent_change_YTD: number;
  percent_change_yearly: number;
  percent_change_3_years: number;
  percent_change_5_years: number;
  dividend_yield: number;
  market_cap: number;
  net_income: number;
  total_revenue: number;
  ttm_pe: number;
  expenseRatio: number;
}

interface IAssetPrice {
  date: string;
  value: number;
}

interface IQuoteResponse {
  price: number;
  percent_change_daily: number;
}

interface IFredObservation {
  date: string;
  value: number;
}

interface ITicketPayload {
  title: string;
  description: string;
}

interface ITicketResponse extends ITicketPayload {
  id: number;
  status: "OPEN" | "IN_REVIEW" | "CLOSED";
  user: number;
  created_at: string;
  updated_at: string;
}

interface IBlogTaxonomy {
  name: string;
  slug: string;
}

interface IRawBlogPostListItem {
  title: string;
  slug: string;
  excerpt: string;
  cover_image_url: string;
  published_at: string;
  created_at: string;
  updated_at: string;
  author_username: string | null;
  categories: IBlogTaxonomy[];
  tags: IBlogTaxonomy[];
}

interface IRawBlogPostDetail extends IRawBlogPostListItem {
  body_markdown: string;
}

interface IPaginatedResponse<T> {
  count: number;
  next: string | null;
  previous: string | null;
  results: T[];
}

const changeflowBaseUrl =
  import.meta.env.VITE_APP_DJANGO_CHANGEFLOW_URL ||
  import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.replace(
    "/portfolio/api/",
    "/changeflow/api/"
  );

const blogBaseUrl =
  import.meta.env.VITE_APP_DJANGO_BLOG_URL ||
  import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.replace(
    "/portfolio/api/",
    "/blog/api/"
  );

const axiosBaseQuery =
  (): BaseQueryFn<{
    url: string;
    method: AxiosRequestConfig["method"];
    data?: AxiosRequestConfig["data"];
    params?: AxiosRequestConfig["params"];
    baseUrl?: string;
    withAuth?: boolean;
  }> =>
    async ({ url, method, data, params, baseUrl, withAuth = true }, api) => {
      try {
        const state = api.getState() as RootState;
        const access = state.user.access;
        const result = await axios({
          url: (baseUrl || import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL) + url,
          method,
          data,
          params: params,
          headers: withAuth && access ? { Authorization: `Bearer ${access}` } : undefined,
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
  tagTypes: ["Assets", "Accounts"],
  endpoints: (builder) => ({
    getAssets: builder.query<IAsset[], number | void>({
      query: (accountId) => ({
        url: "assets/",
        method: "GET",
        params: accountId ? { account_id: accountId } : undefined,
      }),
      transformResponse: (response: IRawAsset[]): IAsset[] => {
        return response.map((item) => ({
          id: item.id,
          ticker: item.ticker,
          shares: Number(item.shares),
          buyDate: item.buy_date,
          buyPrice: Number(item.buy_price),
          snp500PriceBuy: Number(item.buy_SnP500),
          sellDate: item.sell_date,
          sellPrice: item.sell_price ? Number(item.sell_price) : null,
          snp500PriceSell: item.sell_SnP500 ? Number(item.sell_SnP500) : null,
          account: item.account,
        }));
      },
      providesTags: [{ type: "Assets", id: "LIST" }],
    }),
    getAccounts: builder.query<{ id: number, name: string, account_type: string }[], void>({
      query: () => ({
        url: "accounts/",
        method: "GET",
      }),
      providesTags: [{ type: "Accounts", id: "LIST" }],
    }),
    getAccount: builder.query<{ id: number, name: string, account_type: string }, number>({
      query: (id) => ({
        url: `accounts/${id}/`,
        method: "GET",
      }),
      providesTags: (_result, _error, id) => [{ type: "Accounts", id }],
    }),
    postNewAsset: builder.mutation<IAsset, { ticker: string; shares: number; buy_date: string; account_id?: number }>({
      query: (newAsset) => ({
        url: "assets/",
        method: "POST",
        data: newAsset,
      }),
      invalidatesTags: [{ type: "Assets", id: "LIST" }],
    }),
    createAccount: builder.mutation<{ id: number; name: string; account_type: string }, { name: string; account_type: string }>({
      query: (newAccount) => ({
        url: "accounts/",
        method: "POST",
        data: newAccount,
      }),
      invalidatesTags: [{ type: "Accounts", id: "LIST" }],
    }),
    postTicket: builder.mutation<ITicketResponse, ITicketPayload>({
      query: (ticket) => ({
        url: "tickets/",
        method: "POST",
        data: ticket,
        baseUrl: changeflowBaseUrl,
      }),
    }),
    getBlogPosts: builder.query<
      IPaginatedResponse<IRawBlogPostListItem>,
      { search?: string; category?: string; tag?: string; page?: number; page_size?: number } | void
    >({
      query: (params) => ({
        url: "posts/",
        method: "GET",
        params,
        baseUrl: blogBaseUrl,
        withAuth: false,
      }),
    }),
    getBlogPost: builder.query<IRawBlogPostDetail, string>({
      query: (slug) => ({
        url: `posts/${slug}/`,
        method: "GET",
        baseUrl: blogBaseUrl,
        withAuth: false,
      }),
    }),
    getBlogCategories: builder.query<IBlogTaxonomy[], void>({
      query: () => ({
        url: "categories/",
        method: "GET",
        baseUrl: blogBaseUrl,
        withAuth: false,
      }),
    }),
    getBlogTags: builder.query<IBlogTaxonomy[], void>({
      query: () => ({
        url: "tags/",
        method: "GET",
        baseUrl: blogBaseUrl,
        withAuth: false,
      }),
    }),
    getAsset: builder.query<IAsset, number>({
      query: (id) => ({
        url: `assets/${id}`,
        method: "GET",
      }),
      transformResponse: (response: IRawAsset): IAsset => {
        return {
          id: response.id,
          ticker: response.ticker,
          shares: Number(response.shares),
          buyDate: response.buy_date,
          buyPrice: Number(response.buy_price),
          snp500PriceBuy: Number(response.buy_SnP500),
          sellDate: response.sell_date,
          sellPrice: response.sell_price ? Number(response.sell_price) : null,
          snp500PriceSell: response.sell_SnP500 ? Number(response.sell_SnP500) : null,
          account: response.account,
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
    patchAsset: builder.mutation<IAsset, { id: number; [key: string]: unknown }>({
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
        withAuth: false,
      }),
      transformResponse: (
        response: IRawAssetInfoItem[] | { data: IRawAssetInfoItem[] }
      ): Record<string, IEquityInfo | IETFInfo> => {
        const items: IRawAssetInfoItem[] = Array.isArray(response) ? response : response.data;
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
              dividendYield: item.dividend_yield,
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
      IAssetPrice[],
      string
    >({
      query: (ticker) => ({
        url: "asset-prices/",
        method: "GET",
        params: { ticker: ticker },
        withAuth: false,
      }),
    }),
    getAssetDividends: builder.query<IDividendRow[], string>({
      query: (ticker) => ({
        url: "asset-dividends/",
        method: "GET",
        params: { ticker },
        withAuth: false,
      }),
    }),
    getAssetSplits: builder.query<ISplitRow[], string>({
      query: (ticker) => ({
        url: "asset-splits/",
        method: "GET",
        params: { ticker },
        withAuth: false,
      }),
    }),
    getFredData: builder.query<
      Record<string, IFredObservation[]>,
      { series_id: string; compute_yoy?: boolean }[]
    >({
      query: (seriesList) => ({
        url: "fred-data/",
        method: "POST",
        data: seriesList,
      }),
    }),
    getQuote: builder.query<IQuoteResponse, string>({
      query: (ticker) => ({
        url: "quote/",
        method: "GET",
        params: { symbol: ticker },
      }),
    }),
    getSecEdgarData: builder.query<ISECData, string>({
      query: (ticker) => ({
        url: `company-fact-sheet?ticker=${ticker}`,
        method: "GET",
        baseUrl: import.meta.env.VITE_APP_SPRINGBOOT_URL,
      }),
    }),
    getSecQuarters: builder.query<ISECQuartersResponse, string>({
      query: (ticker) => ({
        url: `quarters?ticker=${ticker}`,
        method: "GET",
        baseUrl: import.meta.env.VITE_APP_SPRINGBOOT_URL,
      }),
    }),
    getDjangoQuarters: builder.query<IYFinanceQuarter[], string>({
      query: (ticker) => ({
        url: "quarterly-data/",
        method: "GET",
        params: { ticker },
      }),
    }),
    getSecEdgarDataBatch: builder.query<ISECData[], string[]>({
      async queryFn(tickers, _queryApi, _extraOptions, fetchWithBQ) {
        const results = await Promise.all(
          tickers.map((ticker) =>
            fetchWithBQ({
              url: `company-fact-sheet?ticker=${ticker}`,
              method: "GET",
              baseUrl: import.meta.env.VITE_APP_SPRINGBOOT_URL,
            })
          )
        );
        const data = results
          .filter((r) => r.data)
          .map((r) => r.data as ISECData);
        return { data };
      },
    }),
    getIexPrices: builder.query<IIexPricesResponse, string>({
      query: (ticker) => ({
        url: `prices?ticker=${ticker}`,
        method: "GET",
        baseUrl: import.meta.env.VITE_APP_SPRINGBOOT_URL,
      }),
    }),
    getIexDividends: builder.query<IIexDividendsResponse, string>({
      query: (ticker) => ({
        url: `dividends?ticker=${ticker}`,
        method: "GET",
        baseUrl: import.meta.env.VITE_APP_SPRINGBOOT_URL,
      }),
    }),
    getIexSplits: builder.query<IIexSplitsResponse, string>({
      query: (ticker) => ({
        url: `splits?ticker=${ticker}`,
        method: "GET",
        baseUrl: import.meta.env.VITE_APP_SPRINGBOOT_URL,
      }),
    }),
    getFilingSummaries: builder.query<IFilingSummary[], string>({
      query: (ticker) => ({
        url: `filing-summaries?ticker=${ticker}`,
        method: "GET",
        baseUrl: import.meta.env.VITE_APP_SPRINGBOOT_URL,
      }),
      transformResponse: (response: { ticker: string; summaries: IFilingSummary[] }) => response.summaries,
    }),

    getIndexes: builder.query<IMarketIndex[], void>({
      query: () => ({
        url: "indexes",
        method: "GET",
        baseUrl: import.meta.env.VITE_APP_SPRINGBOOT_URL,
        withAuth: false,
      }),
    }),

    getIndexMembers: builder.query<IIndexMemberRow[], string>({
      query: (code) => ({
        url: "index-members",
        method: "GET",
        params: { code },
        baseUrl: import.meta.env.VITE_APP_SPRINGBOOT_URL,
        withAuth: false,
      }),
    }),

    getIwbReferenceHoldings: builder.query<IIwbReferenceHolding[], void>({
      query: () => ({
        url: "iwb-reference-holdings",
        method: "GET",
        baseUrl: import.meta.env.VITE_APP_SPRINGBOOT_URL,
        withAuth: false,
      }),
    }),
  }),
});

export const {
  useGetAssetInfosQuery,
  useLazyGetAssetInfosQuery,
  useGetAssetPricesQuery,
  useGetAssetDividendsQuery,
  useGetAssetSplitsQuery,
  useGetAssetsQuery,
  usePostNewAssetMutation,
  useGetBlogPostsQuery,
  useGetBlogPostQuery,
  useGetBlogCategoriesQuery,
  useGetBlogTagsQuery,
  useGetAssetQuery,
  useDeleteAssetMutation,
  usePatchAssetMutation,
  useGetFredDataQuery,
  useGetQuoteQuery,
  useCreateAccountMutation,
  usePostTicketMutation,
  useGetAccountsQuery,
  useGetAccountQuery,
  useGetSecEdgarDataQuery,
  useGetSecQuartersQuery,
  useGetDjangoQuartersQuery,
  useGetSecEdgarDataBatchQuery,
  useGetIexPricesQuery,
  useGetIexDividendsQuery,
  useGetIexSplitsQuery,
  useGetFilingSummariesQuery,
  useGetIndexesQuery,
  useGetIndexMembersQuery,
  useGetIwbReferenceHoldingsQuery,
} = api;
