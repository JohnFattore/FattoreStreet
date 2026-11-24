import { createApi } from "@reduxjs/toolkit/query/react";
import type {
  IAsset,
  IChatMessage,
  IConversation,
  IConversationDetail,
  ICurrentUser,
  IDividendRow,
  IEquityInfo,
  IETFInfo,
  IMonitoringJobsResponse,
  IMonitoringLogsResponse,
  IMonitoringMetricsResponse,
  IMonitoringSource,
  IMonitoringSummaryResponse,
  IMonitoringErrorPath,
  IMonitoringTrafficResponse,
  IMonitoringUserActivityResponse,
  IRestaurant,
  IReview,
  ISplitRow,
  IYFinanceQuarter,
} from "../../interfaces";
import { logout } from "../../reducers/authActions";
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

/** Raw snake_case shape returned by Django for a chatbot conversation */
interface IRawConversation {
  id: number;
  title: string;
  created_at: string;
  last_message_at: string;
}

interface IRawConversationDetail extends IRawConversation {
  interactions: IRawChatInteraction[];
}

const toConversation = (row: IRawConversation): IConversation => ({
  id: row.id,
  title: row.title,
  createdAt: row.created_at,
  lastMessageAt: row.last_message_at,
});

const toConversationDetail = (
  row: IRawConversationDetail,
): IConversationDetail => ({
  ...toConversation(row),
  messages: row.interactions.flatMap((interaction): IChatMessage[] => [
    {
      role: "user",
      text: interaction.input_text,
      timestamp: interaction.timestamp,
    },
    {
      role: "model",
      text: interaction.output_text,
      timestamp: interaction.timestamp,
    },
  ]),
});

interface IRawMonitoringErrorPath {
  method: string;
  path: string;
  status: number;
  error_count: number;
}

const toErrorPath = (row: IRawMonitoringErrorPath): IMonitoringErrorPath => ({
  method: row.method,
  path: row.path,
  status: row.status,
  errorCount: row.error_count,
});

/** Shared by the form and the message list so a new conversation's first question shows while it's asked */
export const NEW_CONVERSATION_CACHE_KEY = "newConversation";

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
  baseQuery: axiosBaseQuery({
    defaultBaseUrl: import.meta.env.VITE_APP_DJANGO_URL,
    refreshPath: "users/api/token/refresh/",
    onAuthFailure: (dispatch) => {
      dispatch(logout());
      dispatch(djangoApi.util.resetApiState());
    },
  }),
  tagTypes: [
    "Assets",
    "Accounts",
    "Restaurants",
    "RestaurantRecommendations",
    "Reviews",
    "Conversations",
  ],
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
        arg,
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
    getRestaurants: builder.query<
      IRestaurant[],
      { state: string; city: string }
    >({
      query: ({ state, city }) => ({
        url: "restaurants/api/restaurant-list/",
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
    postReview: builder.mutation<
      void,
      { restaurant: number; rating: number; comment: string }
    >({
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
    getConversations: builder.query<IConversation[], void>({
      query: () => ({
        url: "chatbot/api/conversations/",
        method: "GET",
      }),
      transformResponse: (response: IRawConversation[]): IConversation[] =>
        response.map(toConversation),
      providesTags: [{ type: "Conversations", id: "LIST" }],
    }),
    getConversation: builder.query<IConversationDetail, number>({
      query: (id) => ({
        url: `chatbot/api/conversations/${id}/`,
        method: "GET",
      }),
      transformResponse: toConversationDetail,
      providesTags: (_result, _error, id) => [{ type: "Conversations", id }],
    }),
    // Starting a conversation asks its first question in the same request
    createConversation: builder.mutation<IConversationDetail, string>({
      query: (message) => ({
        url: "chatbot/api/conversations/",
        method: "POST",
        data: { message },
      }),
      transformResponse: toConversationDetail,
      async onQueryStarted(_message, { dispatch, queryFulfilled }) {
        try {
          const { data } = await queryFulfilled;
          // Seed the detail cache so opening the new conversation needs no refetch
          dispatch(
            djangoApi.util.upsertQueryData("getConversation", data.id, data),
          );
        } catch {
          // The form shows the error
        }
      },
      invalidatesTags: [{ type: "Conversations", id: "LIST" }],
    }),
    postConversationMessage: builder.mutation<
      IChatMessage,
      { conversationId: number; message: string }
    >({
      query: ({ conversationId, message }) => ({
        url: `chatbot/api/conversations/${conversationId}/messages/`,
        method: "POST",
        data: { message },
      }),
      transformResponse: (response: { message: string }): IChatMessage => ({
        role: "model",
        text: response.message,
      }),
      async onQueryStarted(
        { conversationId, message },
        { dispatch, queryFulfilled },
      ) {
        // Optimistically show the user's message; roll it back if the request fails
        const patchResult = dispatch(
          djangoApi.util.updateQueryData(
            "getConversation",
            conversationId,
            (draft) => {
              draft.messages.push({ role: "user", text: message });
            },
          ),
        );
        try {
          const { data } = await queryFulfilled;
          dispatch(
            djangoApi.util.updateQueryData(
              "getConversation",
              conversationId,
              (draft) => {
                draft.messages.push(data);
              },
            ),
          );
        } catch {
          patchResult.undo();
        }
      },
      // A new message moves the conversation to the top of the list
      invalidatesTags: [{ type: "Conversations", id: "LIST" }],
    }),
    renameConversation: builder.mutation<void, { id: number; title: string }>({
      query: ({ id, title }) => ({
        url: `chatbot/api/conversations/${id}/`,
        method: "PATCH",
        data: { title },
      }),
      invalidatesTags: (_result, _error, { id }) => [
        { type: "Conversations", id: "LIST" },
        { type: "Conversations", id },
      ],
    }),
    deleteConversation: builder.mutation<void, number>({
      query: (id) => ({
        url: `chatbot/api/conversations/${id}/`,
        method: "DELETE",
      }),
      invalidatesTags: [{ type: "Conversations", id: "LIST" }],
    }),
    // A POST, but a query: the analysis is a pure function of the figures sent, so the
    // same comparison reuses its cached answer instead of asking the model again
    getCompanyComparisonAnalysis: builder.query<
      string,
      { companies: Record<string, string | null>[] }
    >({
      query: (body) => ({
        url: "chatbot/api/company-comparison/",
        method: "POST",
        data: body,
      }),
      transformResponse: (response: { analysis: string }) => response.analysis,
      // Each answer costs a model call, so keep it for the session's revisits
      keepUnusedDataFor: 3600,
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
    getAccounts: builder.query<
      { id: number; name: string; account_type: string }[],
      void
    >({
      query: () => ({
        url: "portfolio/api/accounts/",
        method: "GET",
      }),
      providesTags: [{ type: "Accounts", id: "LIST" }],
    }),
    getAccount: builder.query<
      { id: number; name: string; account_type: string },
      number
    >({
      query: (id) => ({
        url: `portfolio/api/accounts/${id}/`,
        method: "GET",
      }),
      providesTags: (_result, _error, id) => [{ type: "Accounts", id }],
    }),
    postNewAsset: builder.mutation<
      IAsset,
      { ticker: string; shares: number; buy_date: string; account_id?: number }
    >({
      query: (newAsset) => ({
        url: "portfolio/api/assets/",
        method: "POST",
        data: newAsset,
      }),
      invalidatesTags: [{ type: "Assets", id: "LIST" }],
    }),
    createAccount: builder.mutation<
      { id: number; name: string; account_type: string },
      { name: string; account_type: string }
    >({
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
      {
        search?: string;
        category?: string;
        exclude_category?: string;
        tag?: string;
        page?: number;
        page_size?: number;
      } | void
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
    patchAsset: builder.mutation<
      IAsset,
      { id: number; [key: string]: unknown }
    >({
      query: ({ id, ...patch }) => ({
        url: `portfolio/api/assets/${id}/`,
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
        url: "portfolio/api/asset-info/",
        method: "GET",
        params: { tickers: tickers.join(",") },
        withAuth: false,
      }),
      transformResponse: (
        response: IRawAssetInfoItem[] | { data: IRawAssetInfoItem[] },
      ): Record<string, IEquityInfo | IETFInfo> => {
        const items: IRawAssetInfoItem[] = Array.isArray(response)
          ? response
          : response.data;
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
              `Unknown type '${item.type}' in asset-info response`,
            );
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
    getCurrentUser: builder.query<ICurrentUser, void>({
      query: () => ({
        url: "users/api/me/",
        method: "GET",
      }),
      transformResponse: (raw: {
        id: number;
        username: string;
        email: string;
        is_staff: boolean;
      }): ICurrentUser => ({
        id: raw.id,
        username: raw.username,
        email: raw.email,
        isStaff: raw.is_staff,
      }),
    }),
    getMonitoringSources: builder.query<IMonitoringSource[], void>({
      query: () => ({
        url: "monitoring/api/sources/",
        method: "GET",
      }),
      transformResponse: (raw: {
        sources: {
          key: string;
          label: string;
          log_group: string;
          category: "service" | "job" | "system";
        }[];
      }): IMonitoringSource[] =>
        raw.sources.map((s) => ({
          key: s.key,
          label: s.label,
          logGroup: s.log_group,
          category: s.category,
        })),
    }),
    getMonitoringLogs: builder.query<
      IMonitoringLogsResponse,
      {
        source: string;
        start?: string;
        end?: string;
        minutes?: number;
        q?: string;
        level?: string;
        nextToken?: string;
        limit?: number;
      }
    >({
      query: ({ source, start, end, minutes, q, level, nextToken, limit }) => ({
        url: "monitoring/api/logs/",
        method: "GET",
        params: {
          source,
          ...(start ? { start } : {}),
          ...(end ? { end } : {}),
          ...(minutes ? { minutes } : {}),
          ...(q ? { q } : {}),
          ...(level ? { level } : {}),
          ...(nextToken ? { next_token: nextToken } : {}),
          ...(limit ? { limit } : {}),
        },
      }),
      transformResponse: (raw: {
        source: string;
        log_group: string;
        events: {
          event_id: string;
          timestamp: string;
          timestamp_ms: number;
          log_stream: string;
          level: "ERROR" | "WARN" | "INFO" | "DEBUG";
          message: string;
        }[];
        next_token: string | null;
      }): IMonitoringLogsResponse => ({
        source: raw.source,
        logGroup: raw.log_group,
        events: raw.events.map((e) => ({
          eventId: e.event_id,
          timestamp: e.timestamp,
          timestampMs: e.timestamp_ms,
          logStream: e.log_stream,
          level: e.level,
          message: e.message,
        })),
        nextToken: raw.next_token,
      }),
    }),
    getMonitoringSummary: builder.query<
      IMonitoringSummaryResponse,
      { hours?: number } | void
    >({
      query: (arg) => ({
        url: "monitoring/api/summary/",
        method: "GET",
        params: arg?.hours ? { hours: arg.hours } : undefined,
      }),
      transformResponse: (raw: {
        hours: number;
        totals: {
          source: string;
          label: string;
          category: string;
          error_count: number;
          warn_count: number;
        }[];
        buckets: {
          bucket: string;
          error_count: number;
          warn_count: number;
        }[];
      }): IMonitoringSummaryResponse => ({
        hours: raw.hours,
        totals: raw.totals.map((t) => ({
          source: t.source,
          label: t.label,
          category: t.category,
          errorCount: t.error_count,
          warnCount: t.warn_count,
        })),
        buckets: raw.buckets.map((b) => ({
          bucket: b.bucket,
          errorCount: b.error_count,
          warnCount: b.warn_count,
        })),
      }),
    }),
    getMonitoringTraffic: builder.query<
      IMonitoringTrafficResponse,
      { hours?: number } | void
    >({
      query: (arg) => ({
        url: "monitoring/api/traffic/",
        method: "GET",
        params: arg?.hours ? { hours: arg.hours } : undefined,
      }),
      transformResponse: (raw: {
        hours: number;
        total_requests: number;
        total_4xx: number;
        total_5xx: number;
        api_4xx: {
          total: number;
          unauthorized: number;
          forbidden: number;
          not_found: number;
          other: number;
        };
        non_api_4xx: number;
        buckets: {
          bucket: string;
          total_requests: number;
          status_4xx: number;
          status_5xx: number;
        }[];
        top_5xx_paths: IRawMonitoringErrorPath[];
        top_api_4xx_paths: IRawMonitoringErrorPath[];
      }): IMonitoringTrafficResponse => ({
        hours: raw.hours,
        totalRequests: raw.total_requests,
        total4xx: raw.total_4xx,
        total5xx: raw.total_5xx,
        api4xx: {
          total: raw.api_4xx.total,
          unauthorized: raw.api_4xx.unauthorized,
          forbidden: raw.api_4xx.forbidden,
          notFound: raw.api_4xx.not_found,
          other: raw.api_4xx.other,
        },
        nonApi4xx: raw.non_api_4xx,
        buckets: raw.buckets.map((b) => ({
          bucket: b.bucket,
          totalRequests: b.total_requests,
          status4xx: b.status_4xx,
          status5xx: b.status_5xx,
        })),
        top5xxPaths: raw.top_5xx_paths.map(toErrorPath),
        topApi4xxPaths: raw.top_api_4xx_paths.map(toErrorPath),
      }),
    }),
    getMonitoringJobs: builder.query<IMonitoringJobsResponse, void>({
      query: () => ({
        url: "monitoring/api/jobs/",
        method: "GET",
      }),
      transformResponse: (raw: {
        jobs: {
          key: string;
          label: string;
          schedule: string;
          expected_interval_hours: number;
          stale_after_hours: number;
          last_run_at: string | null;
          last_success_at: string | null;
          duration_seconds: number | null;
          exit_code: number | null;
          status: "SUCCESS" | "FAILED" | "UNKNOWN";
          is_stale: boolean;
          stopped_reason: string;
          task_arn: string;
        }[];
      }): IMonitoringJobsResponse => ({
        jobs: raw.jobs.map((j) => ({
          key: j.key,
          label: j.label,
          schedule: j.schedule,
          expectedIntervalHours: j.expected_interval_hours,
          staleAfterHours: j.stale_after_hours,
          lastRunAt: j.last_run_at,
          lastSuccessAt: j.last_success_at,
          durationSeconds: j.duration_seconds,
          exitCode: j.exit_code,
          status: j.status,
          isStale: j.is_stale,
          stoppedReason: j.stopped_reason,
          taskArn: j.task_arn,
        })),
      }),
    }),
    getMonitoringMetrics: builder.query<
      IMonitoringMetricsResponse,
      { hours?: number } | void
    >({
      query: (arg) => ({
        url: "monitoring/api/metrics/",
        method: "GET",
        params: arg?.hours ? { hours: arg.hours } : undefined,
      }),
      transformResponse: (raw: {
        hours: number;
        instance_id: string;
        configured: boolean;
        latest: {
          cpu_percent: number | null;
          mem_used_percent: number | null;
          disk_root_percent: number | null;
          disk_ebs_percent: number | null;
        };
        series: {
          timestamp: string;
          cpu_percent: number | null;
          mem_used_percent: number | null;
          disk_root_percent: number | null;
          disk_ebs_percent: number | null;
        }[];
      }): IMonitoringMetricsResponse => ({
        hours: raw.hours,
        instanceId: raw.instance_id,
        configured: raw.configured,
        latest: {
          cpuPercent: raw.latest.cpu_percent,
          memUsedPercent: raw.latest.mem_used_percent,
          diskRootPercent: raw.latest.disk_root_percent,
          diskEbsPercent: raw.latest.disk_ebs_percent,
        },
        series: raw.series.map((pt) => ({
          timestamp: pt.timestamp,
          cpuPercent: pt.cpu_percent,
          memUsedPercent: pt.mem_used_percent,
          diskRootPercent: pt.disk_root_percent,
          diskEbsPercent: pt.disk_ebs_percent,
        })),
      }),
    }),
    getMonitoringUserActivity: builder.query<
      IMonitoringUserActivityResponse,
      { hours?: number } | void
    >({
      query: (arg) => ({
        url: "monitoring/api/user-activity/",
        method: "GET",
        params: arg?.hours ? { hours: arg.hours } : undefined,
      }),
      transformResponse: (raw: {
        hours: number;
        active_users: number;
        total_requests: number;
        authenticated_requests: number;
        anonymous_requests: number;
        users: {
          username: string;
          is_staff: boolean;
          request_count: number;
          status_4xx: number;
          status_5xx: number;
          last_seen: string | null;
          last_seen_ms: number;
          top_app: string;
          apps: { app: string; request_count: number }[];
        }[];
      }): IMonitoringUserActivityResponse => ({
        hours: raw.hours,
        activeUsers: raw.active_users,
        totalRequests: raw.total_requests,
        authenticatedRequests: raw.authenticated_requests,
        anonymousRequests: raw.anonymous_requests,
        users: raw.users.map((u) => ({
          username: u.username,
          isStaff: u.is_staff,
          requestCount: u.request_count,
          status4xx: u.status_4xx,
          status5xx: u.status_5xx,
          lastSeen: u.last_seen,
          lastSeenMs: u.last_seen_ms,
          topApp: u.top_app,
          apps: u.apps.map((a) => ({
            app: a.app,
            requestCount: a.request_count,
          })),
        })),
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
  useGetConversationsQuery,
  useGetConversationQuery,
  useCreateConversationMutation,
  usePostConversationMessageMutation,
  useRenameConversationMutation,
  useDeleteConversationMutation,
  useGetCompanyComparisonAnalysisQuery,
  useGetCurrentUserQuery,
  useGetMonitoringSourcesQuery,
  useGetMonitoringLogsQuery,
  useGetMonitoringSummaryQuery,
  useGetMonitoringTrafficQuery,
  useGetMonitoringJobsQuery,
  useGetMonitoringMetricsQuery,
  useGetMonitoringUserActivityQuery,
} = djangoApi;
