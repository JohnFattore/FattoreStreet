import { useMemo } from "react";
import { Alert } from "react-bootstrap";
import { SortableTable } from "./SortableTable";
import { useGetIwbReferenceHoldingsQuery } from "../functions/api";
import type { IIndexMemberRow } from "../interfaces";
import { formatPercent } from "../functions/helperFunctions";
import {
  buildIwbWeightMap,
  buildFattore1000IwbCompareRows,
  summarizeFattore1000IwbOverlap,
} from "../functions/fattore1000IwbCompare";
import type { Fattore1000IwbCompareRow } from "../functions/fattore1000IwbCompare";

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
