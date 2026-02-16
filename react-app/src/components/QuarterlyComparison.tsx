import { useState, useMemo } from "react";
import { Card, Row, Col, Table, Form, Spinner, Alert } from "react-bootstrap";
import { useGetSecQuartersQuery, useGetDjangoQuartersQuery } from "../functions/api";
import { formatString } from "../functions/helperFunctions";
import { IQuarter, IYFinanceQuarter } from "../interfaces";

const COMPARE_FIELDS = [
    { label: "Revenue", secKey: "revenues", yfKey: "revenues", format: "money" },
    { label: "Net Income", secKey: "netIncomeLoss", yfKey: "netIncomeLoss", format: "money" },
    { label: "Operating Income", secKey: "operatingIncomeLoss", yfKey: "operatingIncomeLoss", format: "money" },
    { label: "Gross Profit", secKey: "grossProfit", yfKey: "grossProfit", format: "money" },
    { label: "EPS Basic", secKey: "epsBasic", yfKey: "earningsPerShareBasic", format: "eps" },
    { label: "EPS Diluted", secKey: "epsDiluted", yfKey: "earningsPerShareDiluted", format: "eps" },
    { label: "Assets", secKey: "assets", yfKey: "assets", format: "money" },
    { label: "Liabilities", secKey: "liabilities", yfKey: "liabilities", format: "money" },
    { label: "Equity", secKey: "equity", yfKey: "stockholdersEquity", format: "money" },
    { label: "Cash", secKey: "cash", yfKey: "cashAndCashEquivalentsAtCarryingValue", format: "money" },
    { label: "Receivables", secKey: "receivables", yfKey: "accountsReceivableNetCurrent", format: "money" },
    { label: "Inventory", secKey: "inventory", yfKey: "inventoryNet", format: "money" },
    { label: "OCF", secKey: "ocf", yfKey: "netCashProvidedByUsedInOperatingActivities", format: "money" },
    { label: "Dividends", secKey: "dividends", yfKey: "paymentsOfDividends", format: "money" },
    { label: "Buybacks", secKey: "buybacks", yfKey: "paymentsForRepurchaseOfCommonStock", format: "money" },
] as const;

type CompareField = (typeof COMPARE_FIELDS)[number];

function parseSecQuarter(q: string | number): number {
    if (typeof q === "number") return q;
    const match = String(q).match(/Q(\d)/i);
    return match ? parseInt(match[1], 10) : parseInt(String(q), 10) || 0;
}

function formatValue(val: number | null | undefined, format: string): string {
    if (val == null) return "---";
    if (format === "eps") return `$${val.toFixed(2)}`;
    return formatString(val, "money");
}

function formatDiff(diff: number | null, format: string): string {
    if (diff == null) return "---";
    if (format === "eps") {
        const sign = diff >= 0 ? "+" : "";
        return `${sign}$${diff.toFixed(2)}`;
    }
    return formatString(diff, "money");
}

function formatPctDiff(pct: number | null): string {
    if (pct == null) return "---";
    const sign = pct >= 0 ? "+" : "";
    return `${sign}${pct.toFixed(2)}%`;
}

interface ComparisonRow {
    period: string;
    year: number;
    qtr: number;
    secValue: number | null;
    yfValue: number | null;
    diff: number | null;
    pctDiff: number | null;
}

export default function QuarterlyComparison({ ticker }: { ticker: string }) {
    const { data: secData, isLoading: secLoading, error: secError } = useGetSecQuartersQuery(ticker);
    const { data: yfData, isLoading: yfLoading, error: yfError } = useGetDjangoQuartersQuery(ticker);
    const [selectedField, setSelectedField] = useState(0);

    const rows = useMemo(() => {
        const field: CompareField = COMPARE_FIELDS[selectedField];

        const secMap = new Map<string, IQuarter>();
        if (secData?.quarters) {
            for (const q of secData.quarters) {
                const qNum = parseSecQuarter(q.quarter);
                if (qNum > 0) secMap.set(`${q.year}-${qNum}`, q);
            }
        }

        const yfMap = new Map<string, IYFinanceQuarter>();
        if (yfData) {
            for (const q of yfData) {
                yfMap.set(`${q.year}-${q.quarter}`, q);
            }
        }

        const allKeys = new Set<string>([...secMap.keys(), ...yfMap.keys()]);
        const result: ComparisonRow[] = [];

        for (const key of allKeys) {
            const [yearStr, qtrStr] = key.split("-");
            const year = parseInt(yearStr, 10);
            const qtr = parseInt(qtrStr, 10);

            const secQ = secMap.get(key);
            const yfQ = yfMap.get(key);

            const secVal: number | null =
                secQ != null ? (secQ as any)[field.secKey] ?? null : null;
            const yfVal: number | null =
                yfQ != null ? (yfQ as any)[field.yfKey] ?? null : null;

            let diff: number | null = null;
            let pctDiff: number | null = null;
            if (secVal != null && yfVal != null) {
                diff = yfVal - secVal;
                if (secVal !== 0) {
                    pctDiff = (diff / Math.abs(secVal)) * 100;
                }
            }

            result.push({ period: `${year} Q${qtr}`, year, qtr, secValue: secVal, yfValue: yfVal, diff, pctDiff });
        }

        result.sort((a, b) => b.year !== a.year ? b.year - a.year : b.qtr - a.qtr);
        return result;
    }, [secData, yfData, selectedField]);

    const isLoading = secLoading || yfLoading;
    const hasError = secError || yfError;
    const field = COMPARE_FIELDS[selectedField];

    return (
        <Row className="quarterly-comparison">
            <Col md={12}>
                <Card>
                    <Card.Header as="h5" className="bg-dark text-white">
                        SEC vs YFinance Comparison
                    </Card.Header>
                    <Card.Body>
                        {isLoading ? (
                            <div className="text-center"><Spinner animation="border" /></div>
                        ) : hasError ? (
                            <Alert variant="danger">Error loading comparison data.</Alert>
                        ) : (
                            <>
                                <Form.Group style={{ maxWidth: 300 }}>
                                    <Form.Label>Metric</Form.Label>
                                    <Form.Select
                                        value={selectedField}
                                        onChange={(e) => setSelectedField(Number(e.target.value))}
                                    >
                                        {COMPARE_FIELDS.map((f, i) => (
                                            <option key={f.label} value={i}>{f.label}</option>
                                        ))}
                                    </Form.Select>
                                </Form.Group>
                                <Table hover size="sm" responsive>
                                    <thead>
                                        <tr>
                                            <th>Period</th>
                                            <th>SEC EDGAR</th>
                                            <th>YFinance</th>
                                            <th>Difference</th>
                                            <th>% Difference</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {rows.map((row) => {
                                            const isMismatch = row.pctDiff != null && Math.abs(row.pctDiff) > 1;
                                            return (
                                                <tr key={row.period} className={isMismatch ? "table-warning" : ""}>
                                                    <td>{row.period}</td>
                                                    <td>{formatValue(row.secValue, field.format)}</td>
                                                    <td>{formatValue(row.yfValue, field.format)}</td>
                                                    <td className={isMismatch ? "text-danger fw-bold" : ""}>
                                                        {formatDiff(row.diff, field.format)}
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
                                                    No overlapping quarters found.
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
