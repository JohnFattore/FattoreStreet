const friendlyFieldName = (field: string) => {
  if (field === "non_field_errors" || field === "detail") return "Error";
  return field
    .replace(/_/g, " ")         // snake_case to spaced words
    .replace(/\b\w/g, c => c.toUpperCase()); // capitalize each word
};

export function getErrorMessages(errorData: any): string[] {
  if (!errorData) return ["Something went wrong. Please try again."];

  if (typeof errorData.detail === "string") {
    return [errorData.detail];
  }

  if (typeof errorData === "object") {
    return Object.entries(errorData).flatMap(([field, messages]) => {
      const label = friendlyFieldName(field);
      if (Array.isArray(messages)) {
        return messages.map(msg => `${label}: ${msg}`);
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
// phase this out, good error message come from the server, set with axiosFunctions and the reducers
export function translateError(error: string) {
  if (error == "Request failed with status code 401") return "Please Login";
  if (error == "Request failed with status code 500") return "Server Error";
  if (error == "Token is invalid or expired") return "Login Expired";
  else {
    return error;
  }
}
