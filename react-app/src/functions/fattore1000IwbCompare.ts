import type { IIndexMemberRow, IIwbReferenceHolding } from "../interfaces";

export type Fattore1000IwbCompareRow = {
  ticker: string;
  fattoreWeight: number;
  iwbWeight: number | null;
  /** Fattore − IWB when IWB has the symbol */
  diffPp: number | null;
};

/** Formats an ISO date (yyyy-mm-dd) as e.g. "Nov 6, 2026" without a timezone shift. */
export function formatIwbAsOfDate(
  asOf: string | null | undefined,
): string | null {
  if (!asOf) return null;
  const d = new Date(`${asOf}T00:00:00Z`);
  if (Number.isNaN(d.getTime())) return null;
  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  });
}

/**
 * Ticker with share-class separators removed. iShares writes Berkshire Class B as `BRKB`,
 * while index members carry the SEC listing ticker `BRK-B`; both compact to `BRKB`.
 */
export function compactTicker(ticker: string): string {
  return ticker.replace(/[.-]/g, "");
}

/** IWB weights keyed by compact ticker, so separated member tickers find their row. */
export function buildIwbWeightMap(
  holdings: IIwbReferenceHolding[],
): Map<string, number> {
  const m = new Map<string, number>();
  for (const h of holdings) {
    m.set(compactTicker(h.ticker), h.weightPercent);
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
      const key = compactTicker(ticker);
      const fw = m.percent;
      const iw = iwbByTicker.has(key) ? iwbByTicker.get(key)! : null;
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
