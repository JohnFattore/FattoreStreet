import type { BaseQueryFn } from "@reduxjs/toolkit/query";
import axios, { type AxiosError, type AxiosRequestConfig } from "axios";
import type { RootState } from "../../main";

type AxiosBaseQueryArgs = {
  url: string;
  method: AxiosRequestConfig["method"];
  data?: AxiosRequestConfig["data"];
  params?: AxiosRequestConfig["params"];
  baseUrl?: string;
  withAuth?: boolean;
};

function normalizeBaseUrl(raw: string | undefined): string {
  const trimmed = (raw ?? "").trim();
  if (!trimmed) return "";
  return trimmed.endsWith("/") ? trimmed : `${trimmed}/`;
}

export function axiosBaseQuery({
  defaultBaseUrl,
}: {
  defaultBaseUrl: string | undefined;
}): BaseQueryFn<AxiosBaseQueryArgs> {
  const normalizedDefaultBaseUrl = normalizeBaseUrl(defaultBaseUrl);

  return async ({ url, method, data, params, baseUrl, withAuth = true }, api) => {
    try {
      const state = api.getState() as RootState;
      const access = state.user.access;
      const resolvedBaseUrl = normalizeBaseUrl(baseUrl) || normalizedDefaultBaseUrl;

      const result = await axios({
        url: resolvedBaseUrl + url,
        method,
        data,
        params,
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
}

