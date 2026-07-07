import { createApi } from "@reduxjs/toolkit/query/react";
import type {
  IAsset,
  IChatMessage,
  IDividendRow,
  IEquityInfo,
  IETFInfo,
  IRestaurant,
  IReview,
  ISplitRow,
  IYFinanceQuarter,
} from "../../interfaces";
import { axiosBaseQuery } from "./baseQuery";

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

/** Raw snake_case shape returned by Django for a restaurant row */
interface IRawRestaurantRow {
  yelp_id: string;
  name: string;
  address: string;
  state: string;
  city: string;
  latitude: string | number;
  longitude: string | number;
  categories: string;
  stars: string;
  review_count: number;
  id: number;
}

/** Raw snake_case shape returned by Django for a review row */
interface IRawReviewRow {
  restaurant: number;
  restaurant_detail: {
    name: string;
    latitude: string | number;
    longitude: string | number;
  };
  user: number;
  rating: string | number;
  comment: string;
  id: number;
}

/** Raw snake_case shape returned by Django for a chatbot interaction */
interface IRawChatInteraction {
  input_text: string;
  output_text: string;
  timestamp: string;
}

const toRestaurant = (row: IRawRestaurantRow): IRestaurant => ({
  yelp_id: row.yelp_id,
  name: row.name,
  address: row.address,
  state: row.state,
  city: row.city,
  latitude: Number(row.latitude),
  longitude: Number(row.longitude),
  categories: row.categories,
  stars: row.stars,
  review_count: row.review_count,
  id: row.id,
});

export const djangoApi = createApi({
  reducerPath: "djangoApi",
  baseQuery: axiosBaseQuery({ defaultBaseUrl: import.meta.env.VITE_APP_DJANGO_URL }),
  tagTypes: ["Assets", "Accounts", "Restaurants", "RestaurantRecommendations", "Reviews", "Chatbot"],
  endpoints: (builder) => ({
    login: builder.mutation<
      { username: string; access: string; refresh: string },
      { username: string; password: string }
    >({
      query: (credentials) => ({
        url: "users/api/token/",
        method: "POST",
        data: credentials,
        withAuth: false,
      }),
      transformResponse: (
        response: { access: string; refresh: string },
        _meta,
        arg
      ) => ({
        username: arg.username,
        access: response.access,
        refresh: response.refresh,
      }),
    }),
    refreshLogin: builder.mutation<{ access: string }, string>({
      query: (refresh) => ({
        url: "users/api/token/refresh/",
        method: "POST",
        data: { refresh },
        withAuth: false,
      }),
    }),
    postUser: builder.mutation<
      { id: number; username: string; email: string },
      { username: string; password: string; email: string }
    >({
      query: (newUser) => ({
        url: "users/api/users/",
        method: "POST",
        data: newUser,
        withAuth: false,
      }),
    }),
    getRestaurants: builder.query<IRestaurant[], { state: string; city: string }>({
      query: ({ state, city }) => ({
        url: "restaurants/api/restaurant-list-create/",
        method: "GET",
        params: { state, city },
        withAuth: false,
      }),
      transformResponse: (response: IRawRestaurantRow[]): IRestaurant[] =>
        response.map(toRestaurant),
      providesTags: [{ type: "Restaurants", id: "LIST" }],
    }),
    getRestaurantRecommendations: builder.query<IRestaurant[], void>({
      query: () => ({
        url: "restaurants/api/restaurant-recommend/",
        method: "GET",
      }),
      transformResponse: (response: IRawRestaurantRow[]): IRestaurant[] =>
        response.map(toRestaurant),
      providesTags: [{ type: "RestaurantRecommendations", id: "LIST" }],
    }),
    getReviews: builder.query<IReview[], void>({
      query: () => ({
        url: "restaurants/api/review-list/",
        method: "GET",
      }),
      transformResponse: (response: IRawReviewRow[]): IReview[] =>
        response.map((review) => ({
          restaurant: review.restaurant,
          name: review.restaurant_detail.name,
          user: review.user,
          rating: Number(review.rating),
          comment: review.comment,
          latitude: Number(review.restaurant_detail.latitude),
          longitude: Number(review.restaurant_detail.longitude),
          id: review.id,
        })),
      providesTags: [{ type: "Reviews", id: "LIST" }],
    }),
    postReview: builder.mutation<void, { restaurant: number; rating: number; comment: string }>({
      query: (review) => ({
        url: "restaurants/api/review-create/",
        method: "POST",
        // TODO: drop the hardcoded user once Django derives it from the JWT
        data: { ...review, user: 1 },
      }),
      invalidatesTags: [{ type: "Reviews", id: "LIST" }],
    }),
    deleteReview: builder.mutation<void, number>({
      query: (id) => ({
        url: `restaurants/api/review/${id}/`,
        method: "DELETE",
      }),
      invalidatesTags: [{ type: "Reviews", id: "LIST" }],
    }),
    getChatbot: builder.query<IChatMessage[], void>({
      query: () => ({
        url: "chatbot/api/chatbot/",
        method: "GET",
      }),
      transformResponse: (response: IRawChatInteraction[]): IChatMessage[] =>
        response.flatMap((interaction) => [
          { role: "user", text: interaction.input_text, timestamp: interaction.timestamp },
          { role: "model", text: interaction.output_text, timestamp: interaction.timestamp },
        ]),
      providesTags: ["Chatbot"],
    }),
    postChatbot: builder.mutation<IChatMessage, string>({
      query: (message) => ({
        url: "chatbot/api/chatbot/",
        method: "POST",
        data: { message },
      }),
      transformResponse: (response: { message: string }): IChatMessage => ({
        role: "model",
        text: response.message,
      }),
      async onQueryStarted(message, { dispatch, queryFulfilled }) {
        // Optimistically show the user's message; roll it back if the request fails
        const patchResult = dispatch(
          djangoApi.util.updateQueryData("getChatbot", undefined, (draft) => {
            draft.push({ role: "user", text: message });
          })
        );
        try {
          const { data } = await queryFulfilled;
          dispatch(
            djangoApi.util.updateQueryData("getChatbot", undefined, (draft) => {
              draft.push(data);
            })
          );
        } catch {
          patchResult.undo();
        }
      },
    }),
    getAssets: builder.query<IAsset[], number | void>({
      query: (accountId) => ({
        url: "portfolio/api/assets/",
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
    getAccounts: builder.query<{ id: number; name: string; account_type: string }[], void>({
      query: () => ({
        url: "portfolio/api/accounts/",
        method: "GET",
      }),
      providesTags: [{ type: "Accounts", id: "LIST" }],
    }),
    getAccount: builder.query<{ id: number; name: string; account_type: string }, number>({
      query: (id) => ({
        url: `portfolio/api/accounts/${id}/`,
        method: "GET",
      }),
      providesTags: (_result, _error, id) => [{ type: "Accounts", id }],
    }),
    postNewAsset: builder.mutation<IAsset, { ticker: string; shares: number; buy_date: string; account_id?: number }>({
      query: (newAsset) => ({
        url: "portfolio/api/assets/",
        method: "POST",
        data: newAsset,
      }),
      invalidatesTags: [{ type: "Assets", id: "LIST" }],
    }),
    createAccount: builder.mutation<{ id: number; name: string; account_type: string }, { name: string; account_type: string }>({
      query: (newAccount) => ({
        url: "portfolio/api/accounts/",
        method: "POST",
        data: newAccount,
      }),
      invalidatesTags: [{ type: "Accounts", id: "LIST" }],
    }),
    postTicket: builder.mutation<ITicketResponse, ITicketPayload>({
      query: (ticket) => ({
        url: "changeflow/api/tickets/",
        method: "POST",
        data: ticket,
      }),
    }),
    getBlogPosts: builder.query<
      IPaginatedResponse<IRawBlogPostListItem>,
      { search?: string; category?: string; tag?: string; page?: number; page_size?: number } | void
    >({
      query: (params) => ({
        url: "blog/api/posts/",
        method: "GET",
        params,
        withAuth: false,
      }),
    }),
    getBlogPost: builder.query<IRawBlogPostDetail, string>({
      query: (slug) => ({
        url: `blog/api/posts/${slug}/`,
        method: "GET",
        withAuth: false,
      }),
    }),
    deleteAsset: builder.mutation<void, number>({
      query: (id) => ({
        url: `portfolio/api/assets/${id}/`,
        method: "DELETE",
      }),
      invalidatesTags: [{ type: "Assets", id: "LIST" }],
    }),
    patchAsset: builder.mutation<IAsset, { id: number; [key: string]: unknown }>({
      query: ({ id, ...patch }) => ({
        url: `portfolio/api/assets/${id}/`,
        method: "PATCH",
        data: patch,
      }),
      invalidatesTags: [{ type: "Assets", id: "LIST" }],
    }),
    getAssetInfos: builder.query<Record<string, IEquityInfo | IETFInfo>, string[]>({
      query: (tickers) => ({
        url: "portfolio/api/asset-info/",
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
            throw new Error(`Unknown type '${item.type}' in asset-info response`);
          }
        });
        return result;
      },
    }),
    getAssetPrices: builder.query<IAssetPrice[], string>({
      query: (ticker) => ({
        url: "portfolio/api/asset-prices/",
        method: "GET",
        params: { ticker },
        withAuth: false,
      }),
    }),
    getAssetDividends: builder.query<IDividendRow[], string>({
      query: (ticker) => ({
        url: "portfolio/api/asset-dividends/",
        method: "GET",
        params: { ticker },
        withAuth: false,
      }),
    }),
    getAssetSplits: builder.query<ISplitRow[], string>({
      query: (ticker) => ({
        url: "portfolio/api/asset-splits/",
        method: "GET",
        params: { ticker },
        withAuth: false,
      }),
    }),
    getFredData: builder.query<Record<string, IFredObservation[]>, { series_id: string; compute_yoy?: boolean }[]>({
      query: (seriesList) => ({
        url: "portfolio/api/fred-data/",
        method: "POST",
        data: seriesList,
        withAuth: false,
      }),
    }),
    getQuote: builder.query<IQuoteResponse, string>({
      query: (ticker) => ({
        url: "portfolio/api/quote/",
        method: "GET",
        params: { symbol: ticker },
        withAuth: false,
      }),
    }),
    getDjangoQuarters: builder.query<IYFinanceQuarter[], string>({
      query: (ticker) => ({
        url: "portfolio/api/quarterly-data/",
        method: "GET",
        params: { ticker },
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
  useDeleteAssetMutation,
  usePatchAssetMutation,
  useGetFredDataQuery,
  useGetQuoteQuery,
  useLazyGetQuoteQuery,
  useCreateAccountMutation,
  usePostTicketMutation,
  useGetAccountsQuery,
  useGetAccountQuery,
  useGetDjangoQuartersQuery,
  useLoginMutation,
  usePostUserMutation,
  useGetRestaurantsQuery,
  useLazyGetRestaurantRecommendationsQuery,
  useGetReviewsQuery,
  usePostReviewMutation,
  useDeleteReviewMutation,
  useGetChatbotQuery,
  usePostChatbotMutation,
} = djangoApi;

