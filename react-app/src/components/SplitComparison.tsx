import { useMemo } from "react";
import { Alert, Card, Col, Row, Spinner, Table } from "react-bootstrap";
import { useGetAssetSplitsQuery, useGetIexSplitsQuery } from "../functions/api";
import { ISplitRow } from "../interfaces";

interface ComparisonRow {
  myDate: string | null;
  myRatio: number | null;
  yfDate: string | null;
  yfRatio: number | null;
  dateGapDays: number | null;
  diff: number | null;
}

const MATCH_WINDOW_DAYS = 7;
const MIN_COMPARISON_DATE = "2016-01-01";

const toEpochDay = (date: string): number =>
  Math.floor(new Date(`${date}T00:00:00Z`).getTime() / 86_400_000);

const normalizeToAdjustmentRatio = (row: ISplitRow): number | null => {
  if (row.ratio != null && row.ratio > 0) {
    return row.ratio;
  }
  if (row.value == null || row.value <= 0) {
    return null;
  }
  // yfinance split series usually reports new/old multiples (e.g., 4 for 4:1).
  return row.value >= 1 ? 1 / row.value : row.value;
};

const formatRatio = (value: number | null): string => {
  if (value == null) return "---";
  return value.toFixed(6);
};

const buildRows = (mySplits: ISplitRow[], yfSplits: ISplitRow[]): ComparisonRow[] => {
  const usedYfIndexes = new Set<number>();
  const rows: ComparisonRow[] = [];

  const sortedMine = mySplits
    .filter((row) => row.date >= MIN_COMPARISON_DATE)
    .sort((a, b) => a.date.localeCompare(b.date));
  const sortedYf = yfSplits
    .filter((row) => row.date >= MIN_COMPARISON_DATE)
    .sort((a, b) => a.date.localeCompare(b.date));

  for (const mine of sortedMine) {
    const mineDay = toEpochDay(mine.date);
    let bestIndex = -1;
    let bestGap = Number.MAX_SAFE_INTEGER;

    for (let i = 0; i < sortedYf.length; i++) {
      if (usedYfIndexes.has(i)) continue;
      const gap = Math.abs(toEpochDay(sortedYf[i].date) - mineDay);
      if (gap > MATCH_WINDOW_DAYS) continue;
      if (gap < bestGap) {
        bestGap = gap;
        bestIndex = i;
      }
    }

    const myRatio = normalizeToAdjustmentRatio(mine);
    if (bestIndex >= 0) {
      usedYfIndexes.add(bestIndex);
      const yf = sortedYf[bestIndex];
      const yfRatio = normalizeToAdjustmentRatio(yf);
      rows.push({
        myDate: mine.date,
        myRatio,
        yfDate: yf.date,
        yfRatio,
        dateGapDays: bestGap,
        diff: myRatio != null && yfRatio != null ? yfRatio - myRatio : null,
      });
    } else {
      rows.push({
        myDate: mine.date,
        myRatio,
        yfDate: null,
        yfRatio: null,
        dateGapDays: null,
        diff: null,
      });
    }
  }

  for (let i = 0; i < sortedYf.length; i++) {
    if (!usedYfIndexes.has(i)) {
      rows.push({
        myDate: null,
        myRatio: null,
        yfDate: sortedYf[i].date,
        yfRatio: normalizeToAdjustmentRatio(sortedYf[i]),
        dateGapDays: null,
        diff: null,
      });
    }
  }

  rows.sort((a, b) => (b.myDate ?? b.yfDate ?? "").localeCompare(a.myDate ?? a.yfDate ?? ""));
  return rows;
};

export default function SplitComparison({ ticker }: { ticker: string }) {
  const { data: myData, isLoading: myLoading, error: myError } = useGetIexSplitsQuery(ticker);
  const { data: yfData, isLoading: yfLoading, error: yfError } = useGetAssetSplitsQuery(ticker);

  const rows = useMemo(() => buildRows(myData?.splits ?? [], yfData ?? []), [myData, yfData]);
  const isLoading = myLoading || yfLoading;
  const hasError = myError || yfError;

  return (
    <Row className="split-comparison mt-3">
      <Col md={12}>
        <Card>
          <Card.Header as="h5" className="bg-dark text-white">
            Internal vs YFinance Split Comparison
          </Card.Header>
          <Card.Body>
            {isLoading ? (
              <div className="text-center">
                <Spinner animation="border" />
              </div>
            ) : hasError ? (
              <Alert variant="danger">Error loading split comparison data.</Alert>
            ) : (
              <Table hover size="sm" responsive>
                <thead>
                  <tr>
                    <th>Internal Date</th>
                    <th>Internal Ratio</th>
                    <th>YFinance Date</th>
                    <th>YFinance Ratio</th>
                    <th>Date Gap (Days)</th>
                    <th>Difference</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row, idx) => {
                    const isMismatch = row.diff != null && Math.abs(row.diff) > 0.0001;
                    return (
                      <tr
                        key={`${row.myDate ?? "none"}-${row.yfDate ?? "none"}-${idx}`}
                        className={isMismatch ? "table-warning" : ""}
                      >
                        <td>{row.myDate ?? "---"}</td>
                        <td>{formatRatio(row.myRatio)}</td>
                        <td>{row.yfDate ?? "---"}</td>
                        <td>{formatRatio(row.yfRatio)}</td>
                        <td>{row.dateGapDays ?? "---"}</td>
                        <td className={isMismatch ? "text-danger fw-bold" : ""}>
                          {formatRatio(row.diff)}
                        </td>
                      </tr>
                    );
                  })}
                  {rows.length === 0 && (
                    <tr>
                      <td colSpan={6}>No split rows available.</td>
                    </tr>
                  )}
                </tbody>
              </Table>
            )}
          </Card.Body>
        </Card>
      </Col>
    </Row>
  );
}
