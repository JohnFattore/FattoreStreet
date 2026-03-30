import { useMemo } from "react";
import { Alert } from "react-bootstrap";
import { SortableTable } from "./SortableTable";
import { useGetIwbReferenceHoldingsQuery } from "../functions/api";
import type { IIndexMemberRow, IIwbReferenceHolding } from "../interfaces";
import { formatPercent } from "../functions/helperFunctions";

export type Fattore1000IwbCompareRow = {
  ticker: string;
  fattoreWeight: number;
  iwbWeight: number | null;
  /** Fattore − IWB when IWB has the symbol */
  diffPp: number | null;
};

export function buildIwbWeightMap(holdings: IIwbReferenceHolding[]): Map<string, number> {
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

export function summarizeFattore1000IwbOverlap(rows: Fattore1000IwbCompareRow[]): {
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

type Props = {
  members: IIndexMemberRow[];
  membersLoading: boolean;
  membersError: unknown;
};

export function Fattore1000Russell1000CompareTable({
  members,
  membersLoading,
  membersError,
}: Props) {
  const {
    data: iwbHoldings,
    isLoading: iwbLoading,
    error: iwbError,
  } = useGetIwbReferenceHoldingsQuery();

  const iwbByTicker = useMemo(
    () => buildIwbWeightMap(iwbHoldings ?? []),
    [iwbHoldings],
  );

  const rows = useMemo(
    () => buildFattore1000IwbCompareRows(members, iwbByTicker),
    [members, iwbByTicker],
  );

  const summary = useMemo(() => summarizeFattore1000IwbOverlap(rows), [rows]);

  const isLoading = membersLoading || iwbLoading;
  const errors = [membersError, iwbError].filter(Boolean);

  return (
    <div className="mt-4">
      <h5 className="mb-2">Fattore 1000 vs Russell 1000 (IWB)</h5>
      <p className="text-muted small mb-3">
        Side-by-side weights: Fattore 1000 uses cap weights from this app’s metrics; the IWB column is
        the fund-reported weight (% of NAV) from the bundled iShares IWB holdings file (Russell 1000 ETF
        proxy). Dates and methodologies differ, so weights will not match exactly.
      </p>

      {!isLoading && members.length === 0 && (
        <Alert variant="secondary" className="py-2">
          Rebuild the Fattore 1000 index to load members for this comparison.
        </Alert>
      )}

      {members.length > 0 && summary.overlapPercent !== null && (
        <Alert variant="light" className="border py-2 mb-3">
          <div>
            <strong>Symbol overlap:</strong>{" "}
            {summary.matchedCount} of {summary.fattoreCount} Fattore 1000 constituents (
            {summary.overlapPercent.toFixed(1)}%) also appear in the IWB holdings file.
          </div>
          {summary.meanAbsDiffPp !== null && (
            <div className="mt-1">
              <strong>Mean absolute weight difference</strong> (matched names only):{" "}
              {summary.meanAbsDiffPp.toFixed(4)} percentage points (|Fattore − IWB| averaged).
            </div>
          )}
        </Alert>
      )}

      <SortableTable<Fattore1000IwbCompareRow>
        data={rows}
        isLoading={isLoading}
        errors={errors}
        initialSortKey="fattoreWeight"
        initialSortDirection="desc"
        columns={[
          { label: "Ticker", sortKey: "ticker" },
          {
            label: "Fattore 1000",
            sortKey: "fattoreWeight",
            render: (r) => formatPercent(r.fattoreWeight),
          },
          {
            label: "IWB (ETF file)",
            sortKey: "iwbWeight",
            render: (r) =>
              r.iwbWeight === null ? "—" : formatPercent(r.iwbWeight),
          },
          {
            label: "Δ (Fattore − IWB)",
            sortKey: "diffPp",
            render: (r) =>
              r.diffPp === null ? "—" : `${r.diffPp >= 0 ? "+" : ""}${r.diffPp.toFixed(4)} pp`,
          },
        ]}
      />
    </div>
  );
}
