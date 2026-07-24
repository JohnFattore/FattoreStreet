import type { IIndexMemberRow, IIwbReferenceHolding } from "../interfaces";

export type Fattore1000IwbCompareRow = {
  ticker: string;
  fattoreWeight: number;
  iwbWeight: number | null;
  /** Fattore − IWB when IWB has the symbol */
  diffPp: number | null;
};

export function buildIwbWeightMap(
  holdings: IIwbReferenceHolding[],
): Map<string, number> {
  const m = new Map<string, number>();
  for (const h of holdings) {
    m.set(h.ticker, h.weightPercent);
  }
  return m;
}

export function buildFattore1000IwbCompareRows(
  members: IIndexMemberRow[],
  iwbByTicker: Map<string, number>,
): Fattore1000IwbCompareRow[] {
  return [...members]
    .map((m) => {
      const ticker = m.stock.ticker;
      const fw = m.percent;
      const iw = iwbByTicker.has(ticker) ? iwbByTicker.get(ticker)! : null;
      return {
        ticker,
        fattoreWeight: fw,
        iwbWeight: iw,
        diffPp: iw === null ? null : fw - iw,
      };
    })
    .sort((a, b) => b.fattoreWeight - a.fattoreWeight);
}

export function summarizeFattore1000IwbOverlap(
  rows: Fattore1000IwbCompareRow[],
): {
  fattoreCount: number;
  matchedCount: number;
  overlapPercent: number | null;
  meanAbsDiffPp: number | null;
} {
  const fattoreCount = rows.length;
  if (fattoreCount === 0) {
    return {
      fattoreCount: 0,
      matchedCount: 0,
      overlapPercent: null,
      meanAbsDiffPp: null,
    };
  }
  const matched = rows.filter((r) => r.iwbWeight !== null);
  const matchedCount = matched.length;
  const overlapPercent = (matchedCount / fattoreCount) * 100;
  if (matchedCount === 0) {
    return {
      fattoreCount,
      matchedCount: 0,
      overlapPercent: 0,
      meanAbsDiffPp: null,
    };
  }
  const meanAbsDiffPp =
    matched.reduce((acc, r) => acc + Math.abs(r.diffPp ?? 0), 0) / matchedCount;
  return { fattoreCount, matchedCount, overlapPercent, meanAbsDiffPp };
}
