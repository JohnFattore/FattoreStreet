import { useMemo } from "react";
import { Alert, Card, Col, Row, Spinner, Table } from "react-bootstrap";
import { useGetAssetDividendsQuery, useGetIexDividendsQuery } from "../functions/api";
import { IDividendRow } from "../interfaces";

interface ComparisonRow {
  myDate: string | null;
  myDividend: number | null;
  yfDate: string | null;
  yfDividend: number | null;
  dateGapDays: number | null;
  diff: number | null;
}

const MATCH_WINDOW_DAYS = 3;
const MIN_COMPARISON_DATE = "2016-01-01";

const formatCurrency = (value: number | null): string => {
  if (value == null) return "---";
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  }).format(value);
};

const toEpochDay = (date: string): number =>
  Math.floor(new Date(`${date}T00:00:00Z`).getTime() / 86_400_000);

const buildRows = (myDividends: IDividendRow[], yfDividends: IDividendRow[]): ComparisonRow[] => {
  const usedYfIndexes = new Set<number>();
  const rows: ComparisonRow[] = [];

  const sortedMine = myDividends
    .filter((row) => row.date >= MIN_COMPARISON_DATE)
    .sort((a, b) => a.date.localeCompare(b.date));
  const sortedYf = yfDividends
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

    if (bestIndex >= 0) {
      usedYfIndexes.add(bestIndex);
      const yf = sortedYf[bestIndex];
      rows.push({
        myDate: mine.date,
        myDividend: mine.value,
        yfDate: yf.date,
        yfDividend: yf.value,
        dateGapDays: bestGap,
        diff: yf.value - mine.value,
      });
    } else {
      rows.push({
        myDate: mine.date,
        myDividend: mine.value,
        yfDate: null,
        yfDividend: null,
        dateGapDays: null,
        diff: null,
      });
    }
  }

  for (let i = 0; i < sortedYf.length; i++) {
    if (!usedYfIndexes.has(i)) {
      rows.push({
        myDate: null,
        myDividend: null,
        yfDate: sortedYf[i].date,
        yfDividend: sortedYf[i].value,
        dateGapDays: null,
        diff: null,
      });
    }
  }

  rows.sort((a, b) => (b.myDate ?? b.yfDate ?? "").localeCompare(a.myDate ?? a.yfDate ?? ""));
  return rows;
};

export default function DividendComparison({ ticker }: { ticker: string }) {
  const { data: myData, isLoading: myLoading, error: myError } = useGetIexDividendsQuery(ticker);
  const { data: yfData, isLoading: yfLoading, error: yfError } = useGetAssetDividendsQuery(ticker);

  const rows = useMemo(() => buildRows(myData?.dividends ?? [], yfData ?? []), [myData, yfData]);
  const isLoading = myLoading || yfLoading;
  const hasError = myError || yfError;

  return (
    <Row className="dividend-comparison mt-3">
      <Col md={12}>
        <Card>
          <Card.Header as="h5" className="bg-dark text-white">
            Internal vs YFinance Dividend Comparison
          </Card.Header>
          <Card.Body>
            {isLoading ? (
              <div className="text-center">
                <Spinner animation="border" />
              </div>
            ) : hasError ? (
              <Alert variant="danger">Error loading dividend comparison data.</Alert>
            ) : (
              <Table hover size="sm" responsive>
                <thead>
                  <tr>
                    <th>Internal Date</th>
                    <th>Internal Dividend</th>
                    <th>YFinance Date</th>
                    <th>YFinance Dividend</th>
                    <th>Date Gap (Days)</th>
                    <th>Difference</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row, idx) => {
                    const isMismatch =
                      row.diff != null && Math.abs(row.diff) > 0.01;
                    return (
                      <tr key={`${row.myDate ?? "none"}-${row.yfDate ?? "none"}-${idx}`} className={isMismatch ? "table-warning" : ""}>
                        <td>{row.myDate ?? "---"}</td>
                        <td>{formatCurrency(row.myDividend)}</td>
                        <td>{row.yfDate ?? "---"}</td>
                        <td>{formatCurrency(row.yfDividend)}</td>
                        <td>{row.dateGapDays ?? "---"}</td>
                        <td className={isMismatch ? "text-danger fw-bold" : ""}>{formatCurrency(row.diff)}</td>
                      </tr>
                    );
                  })}
                  {rows.length === 0 && (
                    <tr>
                      <td colSpan={6}>No dividend rows available.</td>
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
