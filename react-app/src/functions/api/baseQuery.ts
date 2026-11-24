import type { BaseQueryApi, BaseQueryFn } from "@reduxjs/toolkit/query";
import axios, { type AxiosError, type AxiosRequestConfig } from "axios";
import type { RootState } from "../../main";
import { setAccessToken } from "../../reducers/authActions";
import {
  ACCESS_TOKEN_REFRESH_SKEW_MS,
  hasActiveSession,
  isJwtExpired,
} from "../auth";

type AxiosBaseQueryArgs = {
  url: string;
  method: AxiosRequestConfig["method"];
  data?: AxiosRequestConfig["data"];
  params?: AxiosRequestConfig["params"];
  baseUrl?: string;
  withAuth?: boolean;
  timeout?: number;
};

type AxiosBaseQueryError = {
  status: number | undefined;
  data: unknown;
};

type AxiosBaseQueryOptions = {
  defaultBaseUrl: string | undefined;
  useAuth?: boolean;
  refreshPath?: string;
  onAuthFailure?: (dispatch: BaseQueryApi["dispatch"]) => void;
};

type RefreshOutcome =
  | { status: "refreshed"; access: string }
  | { status: "auth_failed"; error: AxiosBaseQueryError }
  | { status: "transient_error"; error: AxiosBaseQueryError };

const REFRESH_TIMEOUT_MS = 10_000;

function normalizeBaseUrl(raw: string | undefined): string {
  const trimmed = (raw ?? "").trim();
  if (!trimmed) return "";
  return trimmed.endsWith("/") ? trimmed : `${trimmed}/`;
}

function toQueryError(axiosError: unknown): AxiosBaseQueryError {
  const err = axiosError as AxiosError;
  return {
    status: err.response?.status,
    data: err.response?.data || err.message,
  };
}

export function axiosBaseQuery({
  defaultBaseUrl,
  useAuth = true,
  refreshPath,
  onAuthFailure,
}: AxiosBaseQueryOptions): BaseQueryFn<AxiosBaseQueryArgs> {
  const normalizedDefaultBaseUrl = normalizeBaseUrl(defaultBaseUrl);
  const refreshUrl = refreshPath ? normalizedDefaultBaseUrl + refreshPath : "";

  let inFlightRefresh: {
    token: symbol;
    refreshToken: string;
    promise: Promise<RefreshOutcome>;
  } | null = null;

  const triggerAuthFailure = (api: BaseQueryApi) => {
    if (!onAuthFailure) return;
    const { access, refresh, username } = (api.getState() as RootState).user;
    if (access || refresh || username) {
      onAuthFailure(api.dispatch);
    }
  };

  const performRefresh = (
    refreshToken: string,
    api: BaseQueryApi,
  ): Promise<RefreshOutcome> => {
    if (inFlightRefresh && inFlightRefresh.refreshToken === refreshToken) {
      return inFlightRefresh.promise;
    }

    const requestToken = Symbol("refresh");
    const promise = (async (): Promise<RefreshOutcome> => {
      try {
        const response = await axios.post<{ access: string }>(
          refreshUrl,
          { refresh: refreshToken },
          { timeout: REFRESH_TIMEOUT_MS },
        );
        const newAccess = response.data.access;
        // Guard against a concurrent manual logout while the refresh was in flight.
        const currentRefresh = (api.getState() as RootState).user.refresh;
        if (currentRefresh !== refreshToken) {
          return {
            status: "auth_failed",
            error: { status: 401, data: { detail: "Session ended" } },
          };
        }
        api.dispatch(setAccessToken(newAccess));
        return { status: "refreshed", access: newAccess };
      } catch (axiosError) {
        const error = toQueryError(axiosError);
        if (error.status === 401 || error.status === 400) {
          return { status: "auth_failed", error };
        }
        return { status: "transient_error", error };
      } finally {
        if (inFlightRefresh?.token === requestToken) {
          inFlightRefresh = null;
        }
      }
    })();

    inFlightRefresh = { token: requestToken, refreshToken, promise };
    return promise;
  };

  const executeRequest = (
    resolvedUrl: string,
    {
      method,
      data,
      params,
      timeout,
    }: Pick<AxiosBaseQueryArgs, "method" | "data" | "params" | "timeout">,
    accessToken: string,
    shouldSendAuth: boolean,
  ) =>
    axios({
      url: resolvedUrl,
      method,
      data,
      params,
      headers:
        shouldSendAuth && accessToken
          ? { Authorization: `Bearer ${accessToken}` }
          : undefined,
      ...(timeout !== undefined && { timeout }),
    });

  return async (
    { url, method, data, params, baseUrl, withAuth = true, timeout },
    api,
  ) => {
    const resolvedBaseUrl =
      normalizeBaseUrl(baseUrl) || normalizedDefaultBaseUrl;
    const resolvedUrl = resolvedBaseUrl + url;
    const shouldSendAuth = useAuth && withAuth;

    const initialUser = (api.getState() as RootState).user;
    let accessToken = initialUser.access;

    if (shouldSendAuth && (initialUser.access || initialUser.refresh)) {
      if (!hasActiveSession(initialUser)) {
        triggerAuthFailure(api);
        return {
          error: { status: 401, data: { detail: "Session expired" } },
        };
      }

      if (
        refreshUrl &&
        initialUser.refresh &&
        (!accessToken ||
          isJwtExpired(accessToken, ACCESS_TOKEN_REFRESH_SKEW_MS))
      ) {
        const outcome = await performRefresh(initialUser.refresh, api);
        if (outcome.status === "refreshed") {
          accessToken = outcome.access;
        } else if (outcome.status === "auth_failed") {
          triggerAuthFailure(api);
          return { error: outcome.error };
        } else {
          return { error: outcome.error };
        }
      }
    }

    try {
      const result = await executeRequest(
        resolvedUrl,
        { method, data, params, timeout },
        accessToken,
        shouldSendAuth,
      );
      return { data: result.data };
    } catch (axiosError) {
      const err = toQueryError(axiosError);

      if (
        err.status !== 401 ||
        !shouldSendAuth ||
        !accessToken ||
        !refreshUrl
      ) {
        return { error: err };
      }

      const latestUser = (api.getState() as RootState).user;

      // Another in-flight request may have already rotated the access token.
      let retryAccess: string;
      if (
        latestUser.access &&
        latestUser.access !== accessToken &&
        !isJwtExpired(latestUser.access, ACCESS_TOKEN_REFRESH_SKEW_MS)
      ) {
        retryAccess = latestUser.access;
      } else if (latestUser.refresh && !isJwtExpired(latestUser.refresh)) {
        const outcome = await performRefresh(latestUser.refresh, api);
        if (outcome.status === "refreshed") {
          retryAccess = outcome.access;
        } else if (outcome.status === "auth_failed") {
          triggerAuthFailure(api);
          return { error: err };
        } else {
          return { error: outcome.error };
        }
      } else {
        if (latestUser.access === accessToken) {
          triggerAuthFailure(api);
        }
        return { error: err };
      }

      try {
        const retryResult = await executeRequest(
          resolvedUrl,
          { method, data, params, timeout },
          retryAccess,
          true,
        );
        return { data: retryResult.data };
      } catch (retryError) {
        return { error: toQueryError(retryError) };
      }
    }
  };
}
