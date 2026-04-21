import { createApi } from "@reduxjs/toolkit/query/react";
import type {
  IFilingSummary,
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
  baseQuery: axiosBaseQuery({ defaultBaseUrl: import.meta.env.VITE_APP_SPRINGBOOT_URL }),
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
      transformResponse: (response: { ticker: string; summaries: IFilingSummary[] }) =>
        response.summaries,
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
} = springbootApi;

