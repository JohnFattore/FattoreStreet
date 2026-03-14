import { describe, expect, it } from "vitest";
import adminSuccessBarReducer, {
  addTicker,
  removeTicker,
  setValidationError,
} from "../src/reducers/adminSuccessBarReducer";

describe("adminSuccessBarReducer", () => {
  it("adds uppercase ticker and prevents duplicates", () => {
    const withTicker = adminSuccessBarReducer(
      { tickers: [], error: "" },
      addTicker("aapl")
    );
    expect(withTicker.tickers).toEqual(["AAPL"]);
    expect(withTicker.error).toBe("");

    const duplicateAttempt = adminSuccessBarReducer(withTicker, addTicker("AAPL"));
    expect(duplicateAttempt.tickers).toEqual(["AAPL"]);
    expect(duplicateAttempt.error).toContain("already in the list");
  });

  it("removes ticker and sets explicit validation errors", () => {
    const removed = adminSuccessBarReducer(
      { tickers: ["AAPL", "VOO"], error: "" },
      removeTicker("voo")
    );
    expect(removed.tickers).toEqual(["AAPL"]);

    const withError = adminSuccessBarReducer(removed, setValidationError("Ticker is required."));
    expect(withError.error).toBe("Ticker is required.");
  });
});
