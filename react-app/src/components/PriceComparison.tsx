import { useMemo } from "react";
import { Card, Row, Col, Table, Spinner, Alert } from "react-bootstrap";
import { useGetIexPricesQuery, useGetAssetPricesQuery } from "../functions/api";

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
    iexClose: number | null;
    yfClose: number | null;
    diff: number | null;
    pctDiff: number | null;
}

export default function PriceComparison({ ticker }: { ticker: string }) {
    const { data: iexData, isLoading: iexLoading, error: iexError } = useGetIexPricesQuery(ticker);
    const { data: yfData, isLoading: yfLoading, error: yfError } = useGetAssetPricesQuery(ticker);

    const rows = useMemo(() => {
        const iexMap = new Map<string, number>();
        if (iexData?.prices) {
            for (const p of iexData.prices) {
                iexMap.set(p.date, p.close);
            }
        }

        const yfMap = new Map<string, number>();
        if (yfData) {
            for (const p of yfData) {
                yfMap.set(p.date, p.value);
            }
        }

        const allDates = new Set<string>([...iexMap.keys(), ...yfMap.keys()]);
        const result: ComparisonRow[] = [];

        for (const date of allDates) {
            const iexClose = iexMap.get(date) ?? null;
            const yfClose = yfMap.get(date) ?? null;

            let diff: number | null = null;
            let pctDiff: number | null = null;
            if (iexClose != null && yfClose != null) {
                diff = yfClose - iexClose;
                if (iexClose !== 0) {
                    pctDiff = (diff / Math.abs(iexClose)) * 100;
                }
            }

            result.push({ date, iexClose, yfClose, diff, pctDiff });
        }

        result.sort((a, b) => b.date.localeCompare(a.date));
        return result;
    }, [iexData, yfData]);

    const isLoading = iexLoading || yfLoading;
    const hasError = iexError || yfError;

    return (
        <Row className="price-comparison mt-3">
            <Col md={12}>
                <Card>
                    <Card.Header as="h5" className="bg-dark text-white">
                        IEX vs YFinance Price Comparison
                    </Card.Header>
                    <Card.Body>
                        {isLoading ? (
                            <div className="text-center"><Spinner animation="border" /></div>
                        ) : hasError ? (
                            <Alert variant="danger">Error loading comparison data.</Alert>
                        ) : (
                            <Table hover size="sm" responsive>
                                <thead>
                                    <tr>
                                        <th>Date</th>
                                        <th>IEX Close</th>
                                        <th>YFinance Close</th>
                                        <th>Difference</th>
                                        <th>% Difference</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {rows.map((row) => {
                                        const isMismatch = row.pctDiff != null && Math.abs(row.pctDiff) > 1;
                                        return (
                                            <tr key={row.date} className={isMismatch ? "table-warning" : ""}>
                                                <td>{row.date}</td>
                                                <td>{formatCurrency(row.iexClose)}</td>
                                                <td>{formatCurrency(row.yfClose)}</td>
                                                <td className={isMismatch ? "text-danger fw-bold" : ""}>
                                                    {formatCurrency(row.diff)}
                                                </td>
                                                <td className={isMismatch ? "text-danger fw-bold" : ""}>
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
                        )}
                    </Card.Body>
                </Card>
            </Col>
        </Row>
    );
}
