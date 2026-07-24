import { createApi } from "@reduxjs/toolkit/query/react";
import type {
  IFilingSummary,
  IFredObservation,
  IIexDividendsResponse,
  IIexPricesResponse,
  IIexSplitsResponse,
  IIndexMemberRow,
  IMarketIndex,
  IIwbReferenceHolding,
  ISECData,
  ISECQuartersResponse,
} from "../../interfaces";
import { axiosBaseQuery } from "./baseQuery";

export const springbootApi = createApi({
  reducerPath: "springbootApi",
  baseQuery: axiosBaseQuery({
    defaultBaseUrl: import.meta.env.VITE_APP_SPRINGBOOT_URL,
  }),
  endpoints: (builder) => ({
    getSecEdgarData: builder.query<ISECData, string>({
      query: (ticker) => ({
        url: `company-fact-sheet?ticker=${ticker}`,
        method: "GET",
      }),
    }),
    getSecQuarters: builder.query<ISECQuartersResponse, string>({
      query: (ticker) => ({
        url: `quarters?ticker=${ticker}`,
        method: "GET",
      }),
    }),
    getSecEdgarDataBatch: builder.query<ISECData[], string[]>({
      async queryFn(tickers, _queryApi, _extraOptions, fetchWithBQ) {
        const results = await Promise.all(
          tickers.map((ticker) =>
            fetchWithBQ({
              url: `company-fact-sheet?ticker=${ticker}`,
              method: "GET",
            }),
          ),
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
      }),
    }),
    getIexDividends: builder.query<IIexDividendsResponse, string>({
      query: (ticker) => ({
        url: `dividends?ticker=${ticker}`,
        method: "GET",
      }),
    }),
    getIexSplits: builder.query<IIexSplitsResponse, string>({
      query: (ticker) => ({
        url: `splits?ticker=${ticker}`,
        method: "GET",
      }),
    }),
    getFilingSummaries: builder.query<IFilingSummary[], string>({
      query: (ticker) => ({
        url: `filing-summaries?ticker=${ticker}`,
        method: "GET",
      }),
      transformResponse: (response: {
        ticker: string;
        summaries: IFilingSummary[];
      }) => response.summaries,
    }),
    getIndexes: builder.query<IMarketIndex[], void>({
      query: () => ({
        url: "indexes",
        method: "GET",
      }),
    }),
    getIndexMembers: builder.query<IIndexMemberRow[], string>({
      query: (code) => ({
        url: "index-members",
        method: "GET",
        params: { code },
      }),
    }),
    getIwbReferenceHoldings: builder.query<IIwbReferenceHolding[], void>({
      query: () => ({
        url: "iwb-reference-holdings",
        method: "GET",
      }),
    }),
    getFredData: builder.query<
      Record<string, IFredObservation[]>,
      { seriesId: string; computeYoy?: boolean }[]
    >({
      query: (seriesList) => ({
        url: "fred-data",
        method: "POST",
        data: seriesList,
        withAuth: false,
      }),
    }),

    adminAssetLoad: builder.mutation<string, { overwriteExisting?: boolean }>({
      async queryFn(arg, _queryApi, _extraOptions, fetchWithBQ) {
        const params: Record<string, string> = {};
        if (arg.overwriteExisting) params.overwriteExisting = "true";
        const result = await fetchWithBQ({
          url: "admin/asset-load",
          method: "GET",
          params,
          timeout: 0,
        });
        if (
          result.error &&
          (result.error as { status?: number }).status === 404
        ) {
          const fallback = await fetchWithBQ({
            url: "admin/load",
            method: "GET",
            timeout: 0,
          });
          if (fallback.error) return { error: fallback.error };
          return {
            data:
              typeof fallback.data === "string"
                ? fallback.data
                : JSON.stringify(fallback.data),
          };
        }
        if (result.error) return { error: result.error };
        return {
          data:
            typeof result.data === "string"
              ? result.data
              : JSON.stringify(result.data),
        };
      },
    }),
    adminSyncFrames: builder.mutation<string, void>({
      query: () => ({
        url: "admin/sync-frames",
        method: "GET",
      }),
      transformResponse: (response: unknown) =>
        typeof response === "string" ? response : JSON.stringify(response),
    }),
    adminLoadHist: builder.mutation<string, { days: string }>({
      query: ({ days }) => ({
        url: "admin/load-hist",
        method: "GET",
        params: { days },
        timeout: 0,
      }),
      transformResponse: (response: unknown) =>
        typeof response === "string" ? response : JSON.stringify(response),
    }),
    adminAdjustPrices: builder.mutation<
      string,
      {
        ticker?: string;
        force?: boolean;
        etfOnly?: boolean;
        equityOnly?: boolean;
        minConfidence?: number;
      }
    >({
      query: (arg) => {
        const params: Record<string, string> = {};
        if (arg.ticker) params.ticker = arg.ticker;
        if (arg.force) params.force = "true";
        if (arg.etfOnly) params.etfOnly = "true";
        if (arg.equityOnly) params.equityOnly = "true";
        if (arg.minConfidence !== undefined && arg.minConfidence >= 0) {
          params.minConfidence = String(arg.minConfidence);
        }
        return {
          url: "admin/adjust-prices",
          method: "GET",
          params,
          timeout: 0,
        };
      },
      transformResponse: (response: unknown) =>
        typeof response === "string" ? response : JSON.stringify(response),
    }),
    adminSummarizeFilings: builder.mutation<string, { ticker?: string }>({
      query: ({ ticker }) => {
        const params: Record<string, string> = {};
        if (ticker) params.ticker = ticker;
        return {
          url: "admin/summarize-filings",
          method: "GET",
          params,
          timeout: 0,
        };
      },
      transformResponse: (response: unknown) =>
        typeof response === "string" ? response : JSON.stringify(response),
    }),
    adminRefreshIndexMetrics: builder.mutation<
      string,
      { scope?: string; ticker?: string }
    >({
      query: ({ scope, ticker }) => {
        const params: Record<string, string> = {};
        if (ticker) {
          params.ticker = ticker;
        } else if (scope && scope !== "russell1000") {
          params.scope = scope;
        }
        return {
          url: "admin/indexes/refresh-stocks",
          method: "POST",
          params,
          timeout: 0,
        };
      },
      transformResponse: (response: unknown) =>
        typeof response === "string" ? response : JSON.stringify(response),
    }),
    adminRebuildIndexes: builder.mutation<
      string,
      { code?: string; refreshMetrics?: boolean }
    >({
      query: ({ code, refreshMetrics }) => {
        const params: Record<string, string> = {};
        if (refreshMetrics) params.refreshMetrics = "true";
        if (code) params.code = code;
        return {
          url: "admin/indexes/rebuild",
          method: "POST",
          params,
          timeout: 0,
        };
      },
      transformResponse: (response: unknown) =>
        typeof response === "string" ? response : JSON.stringify(response),
    }),
  }),
});

export const {
  useGetSecEdgarDataQuery,
  useGetSecQuartersQuery,
  useGetSecEdgarDataBatchQuery,
  useGetIexPricesQuery,
  useGetIexDividendsQuery,
  useGetIexSplitsQuery,
  useGetFilingSummariesQuery,
  useGetIndexesQuery,
  useGetIndexMembersQuery,
  useGetIwbReferenceHoldingsQuery,
  useGetFredDataQuery,
  useAdminAssetLoadMutation,
  useAdminSyncFramesMutation,
  useAdminLoadHistMutation,
  useAdminAdjustPricesMutation,
  useAdminSummarizeFilingsMutation,
  useAdminRefreshIndexMetricsMutation,
  useAdminRebuildIndexesMutation,
} = springbootApi;
