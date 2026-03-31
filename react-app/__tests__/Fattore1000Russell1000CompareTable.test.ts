// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import {
  buildFattore1000IwbCompareRows,
  buildIwbWeightMap,
  summarizeFattore1000IwbOverlap,
} from "../src/functions/fattore1000IwbCompare";
import type { IIndexMemberRow } from "../src/interfaces";

function member(ticker: string, percent: number): IIndexMemberRow {
  return {
    id: 1,
    percent,
    index: "Fattore 1000",
    outlier: false,
    notes: "",
    stock: {
      ticker,
      name: ticker,
      marketCap: 1,
      volume: 1,
      volumeUSD: 1,
      freeFloat: 1,
      freeFloatMarketCap: 1,
      countryIncorp: "United States",
      countryHQ: "United States",
      stateIncorp: null,
      stateHQ: null,
      securityType: "Common Stock",
      yearIPO: 0,
    },
  };
}

describe("Fattore1000Russell1000CompareTable helpers", () => {
  it("summarizeFattore1000IwbOverlap computes overlap and mean absolute diff", () => {
    const rows = buildFattore1000IwbCompareRows(
      [member("AAA", 10), member("BBB", 5), member("ZZZ", 2)],
      buildIwbWeightMap([
        { ticker: "AAA", weightPercent: 8 },
        { ticker: "BBB", weightPercent: 7 },
      ]),
    );
    const s = summarizeFattore1000IwbOverlap(rows);
    expect(s.fattoreCount).toBe(3);
    expect(s.matchedCount).toBe(2);
    expect(s.overlapPercent).toBeCloseTo((2 / 3) * 100, 5);
    expect(s.meanAbsDiffPp).toBeCloseTo((2 + 2) / 2, 5);
  });
});
