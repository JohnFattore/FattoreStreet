import { useMemo } from "react";
import { Card, Row, Col, Table, Spinner, Alert } from "react-bootstrap";
import { useGetIexPricesQuery, useGetAssetPricesQuery } from "../functions/api";

const MIN_COMPARISON_DATE = "2016-01-01";

const formatCurrency = (val: number | null | undefined): string => {
    if (val == null) return "---";
    return new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD",
        minimumFractionDigits: 2,
        maximumFractionDigits: 4,
    }).format(val);
};

function formatPctDiff(pct: number | null): string {
    if (pct == null) return "---";
    const sign = pct >= 0 ? "+" : "";
    return `${sign}${pct.toFixed(2)}%`;
}

interface ComparisonRow {
    date: string;
    iexAdjustedClose: number | null;
    yfAdjustedClose: number | null;
    diff: number | null;
    pctDiff: number | null;
}

function diffSeverity(pctDiff: number | null): "none" | "moderate" | "high" {
    if (pctDiff == null) return "none";
    const absPct = Math.abs(pctDiff);
    if (absPct > 5) return "high";
    if (absPct >= 1) return "moderate";
    return "none";
}

export default function PriceComparison({ ticker }: { ticker: string }) {
    const { data: iexData, isLoading: iexLoading, error: iexError } = useGetIexPricesQuery(ticker);
    const { data: yfData, isLoading: yfLoading, error: yfError } = useGetAssetPricesQuery(ticker);

    const rows = useMemo(() => {
        const iexMap = new Map<string, number>();
        if (iexData?.prices) {
            for (const p of iexData.prices) {
                if (p.date < MIN_COMPARISON_DATE) continue;
                iexMap.set(p.date, p.adjustedClose ?? p.close);
            }
        }

        const yfMap = new Map<string, number>();
        if (yfData) {
            for (const p of yfData) {
                if (p.date < MIN_COMPARISON_DATE) continue;
                yfMap.set(p.date, p.value);
            }
        }

        // Anchor the comparison window to IEX availability:
        // once IEX has no more dates, we stop rendering additional YFinance-only rows.
        const allDates = Array.from(iexMap.keys());
        const result: ComparisonRow[] = [];

        for (const date of allDates) {
            const iexAdjustedClose = iexMap.get(date) ?? null;
            const yfAdjustedClose = yfMap.get(date) ?? null;

            let diff: number | null = null;
            let pctDiff: number | null = null;
            if (iexAdjustedClose != null && yfAdjustedClose != null) {
                diff = yfAdjustedClose - iexAdjustedClose;
                if (iexAdjustedClose !== 0) {
                    pctDiff = (diff / Math.abs(iexAdjustedClose)) * 100;
                }
            }

            result.push({ date, iexAdjustedClose, yfAdjustedClose, diff, pctDiff });
        }

        result.sort((a, b) => b.date.localeCompare(a.date));
        return result;
    }, [iexData, yfData]);

    const isLoading = iexLoading || yfLoading;
    const hasError = iexError || yfError;
    const overlapSummary = useMemo(() => {
        const overlappingRows = rows.filter((row) => row.pctDiff != null);
        const totalOverlapping = overlappingRows.length;

        const buildMetric = (limitPct: number) => {
            const count = overlappingRows.filter((row) =>
                row.pctDiff != null && Math.abs(row.pctDiff) <= limitPct
            ).length;
            const pct = totalOverlapping > 0 ? (count / totalOverlapping) * 100 : null;
            return { count, pct };
        };

        const withinOne = buildMetric(1);
        const withinThree = buildMetric(3);
        const withinFive = buildMetric(5);
        const withinTwentyFive = buildMetric(25);

        return {
            totalOverlapping,
            withinOne,
            withinThree,
            withinFive,
            withinTwentyFive,
        };
    }, [rows]);

    return (
        <Row className="price-comparison mt-3">
            <Col md={12}>
                <Card>
                    <Card.Header as="h5" className="bg-dark text-white">
                        IEX vs YFinance Adjusted Price Comparison
                    </Card.Header>
                    <Card.Body>
                        {isLoading ? (
                            <div className="text-center"><Spinner animation="border" /></div>
                        ) : hasError ? (
                            <Alert variant="danger">Error loading comparison data.</Alert>
                        ) : (
                            <>
                                <div className="mb-2 text-muted">
                                    {overlapSummary.totalOverlapping > 0
                                        ? `Within 1%: ${overlapSummary.withinOne.count}/${overlapSummary.totalOverlapping} (${overlapSummary.withinOne.pct?.toFixed(2)}%) | Within 3%: ${overlapSummary.withinThree.count}/${overlapSummary.totalOverlapping} (${overlapSummary.withinThree.pct?.toFixed(2)}%) | Within 5%: ${overlapSummary.withinFive.count}/${overlapSummary.totalOverlapping} (${overlapSummary.withinFive.pct?.toFixed(2)}%) | Within 25%: ${overlapSummary.withinTwentyFive.count}/${overlapSummary.totalOverlapping} (${overlapSummary.withinTwentyFive.pct?.toFixed(2)}%)`
                                        : "---"}
                                </div>
                                <Table hover size="sm" responsive>
                                    <thead>
                                        <tr>
                                            <th>Date</th>
                                            <th>IEX Adjusted Close</th>
                                            <th>YFinance Adjusted Close</th>
                                            <th>Difference</th>
                                            <th>% Difference</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {rows.map((row) => {
                                            const severity = diffSeverity(row.pctDiff);
                                            const rowClassName =
                                                severity === "high"
                                                    ? "table-danger"
                                                    : severity === "moderate"
                                                        ? "table-warning"
                                                        : "";
                                            const valueClassName =
                                                severity === "high"
                                                    ? "text-danger fw-bold"
                                                    : severity === "moderate"
                                                        ? "text-warning-emphasis fw-bold"
                                                        : "";
                                            return (
                                                <tr key={row.date} className={rowClassName}>
                                                    <td>{row.date}</td>
                                                    <td>{formatCurrency(row.iexAdjustedClose)}</td>
                                                    <td>{formatCurrency(row.yfAdjustedClose)}</td>
                                                    <td className={valueClassName}>
                                                        {formatCurrency(row.diff)}
                                                    </td>
                                                    <td className={valueClassName}>
                                                        {formatPctDiff(row.pctDiff)}
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                        {rows.length === 0 && (
                                            <tr>
                                                <td colSpan={5}>
                                                    No overlapping dates found.
                                                </td>
                                            </tr>
                                        )}
                                    </tbody>
                                </Table>
                            </>
                        )}
                    </Card.Body>
                </Card>
            </Col>
        </Row>
    );
}
