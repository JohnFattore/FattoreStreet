const friendlyFieldName = (field: string) => {
  if (field === "non_field_errors" || field === "detail") return "Error";
  return field
    .replace(/_/g, " ")         // snake_case to spaced words
    .replace(/\b\w/g, c => c.toUpperCase()); // capitalize each word
};

/** Extract error messages from an RTK Query error (which has a `.data` property). */
export function getApiErrorMessages(error: unknown): string[] {
  if (error && typeof error === 'object' && 'data' in error) {
    return getErrorMessages((error as { data: unknown }).data);
  }
  return getErrorMessages(error);
}

export function getErrorMessages(errorData: unknown): string[] {
  if (!errorData) return ["Something went wrong. Please try again."];

  if (typeof errorData === "object" && errorData !== null) {
    const obj = errorData as Record<string, unknown>;
    if (typeof obj.detail === "string") {
      return [obj.detail];
    }
    return Object.entries(obj).flatMap(([field, messages]) => {
      const label = friendlyFieldName(field);
      if (Array.isArray(messages)) {
        return messages.map((msg: unknown) => `${label}: ${msg}`);
      }
      return [`${label}: ${messages}`];
    });
  }

  return ["An unexpected error occurred."];
}


export function formatString(
  value: string | number | undefined,
  type: string
): string {
  if (typeof value === "undefined") {
    return "undefined";
  }
  switch (type) {
    case "text":
      return String(value);

    case "money":
      // Format as USD currency
      const absValue = Math.abs(Number(value));
      let formattedValue: string;

      if (absValue >= 1_000_000_000) {
        formattedValue =
          new Intl.NumberFormat("en-US", {
            style: "currency",
            currency: "USD",
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          }).format(Number(value) / 1_000_000_000) + " Billion";
      } else if (absValue >= 1_000_000) {
        formattedValue =
          new Intl.NumberFormat("en-US", {
            style: "currency",
            currency: "USD",
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          }).format(Number(value) / 1_000_000) + " Million";
      } else {
        formattedValue = new Intl.NumberFormat("en-US", {
          style: "currency",
          currency: "USD",
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        }).format(Number(value));
      }

      return formattedValue;

    case "amount":
      // Format like money, but without the currency symbol
      return new Intl.NumberFormat("en-US", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(Number(value));

    case "percent":
      // Format as a percentage
      return new Intl.NumberFormat("en-US", {
        style: "percent",
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(Number(value)); // Divide by 100 for percent formatting
    default:
      return String(value); // Fallback: return the value as-is
  }
}
export function formatNumber(v: number | null | undefined): string {
  if (v == null || Number.isNaN(v)) return "";
  return Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(v);
}

export function formatCurrency(v: number | null | undefined): string {
  if (v == null || Number.isNaN(v)) return "";
  return Intl.NumberFormat(undefined, {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 0,
  }).format(v);
}

export function formatPercent(v: number | null | undefined): string {
  if (v == null || Number.isNaN(v)) return "";
  return `${v.toFixed(2)}%`;
}

export function formatLargeCurrency(v: number | null | undefined): string {
  if (v == null || Number.isNaN(v)) return "";
  const abs = Math.abs(v);
  const fmt = (n: number) =>
    Intl.NumberFormat("en-US", {
      style: "currency",
      currency: "USD",
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(n);
  if (abs >= 1_000_000_000_000) {
    return `${fmt(v / 1_000_000_000_000)} Trillion`;
  }
  if (abs >= 1_000_000_000) {
    return `${fmt(v / 1_000_000_000)} Billion`;
  }
  if (abs >= 1_000_000) {
    return `${fmt(v / 1_000_000)} Million`;
  }
  return formatCurrency(v);
}

export function formatLargeNumber(v: number | null | undefined): string {
  if (v == null || Number.isNaN(v)) return "";
  const abs = Math.abs(v);
  const fmt = (n: number) =>
    Intl.NumberFormat("en-US", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(n);
  if (abs >= 1_000_000_000_000) {
    return `${fmt(v / 1_000_000_000_000)} Trillion`;
  }
  if (abs >= 1_000_000_000) {
    return `${fmt(v / 1_000_000_000)} Billion`;
  }
  if (abs >= 1_000_000) {
    return `${fmt(v / 1_000_000)} Million`;
  }
  return formatNumber(v);
}

// phase this out, good error message come from the server, set with axiosFunctions and the reducers
export function translateError(error: string) {
  if (error == "Request failed with status code 401") return "Please Login";
  if (error == "Request failed with status code 500") return "Server Error";
  if (error == "Token is invalid or expired") return "Login Expired";
  else {
    return error;
  }
}


import { useSelector } from 'react-redux';
import { RootState } from '../main';
import { api } from './api';
import { QueryStatus } from '@reduxjs/toolkit/query';

export function useIsEndpointLoading(endpointName: string): boolean {
  return useSelector((state: RootState) =>
    Object.values(state[api.reducerPath].queries).some(
      (query) => query?.endpointName === endpointName && query.status === QueryStatus.pending
    )
  );
}