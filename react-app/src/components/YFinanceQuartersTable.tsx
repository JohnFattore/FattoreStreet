import { Card, Row, Col } from "react-bootstrap";
import { useGetDjangoQuartersQuery } from "../functions/api";
import { SortableTable } from "./SortableTable";
import { formatString } from "../functions/helperFunctions";
import { IYFinanceQuarter } from "../interfaces";

const columns = [
    {
        label: "Period",
        sortKey: "year",
        render: (row: IYFinanceQuarter) => `${row.year} Q${row.quarter}`,
    },
    {
        label: "Period End",
        sortKey: "periodEnd",
        render: (row: IYFinanceQuarter) => row.periodEnd,
    },
    {
        label: "Revenue",
        sortKey: "revenues",
        render: (row: IYFinanceQuarter) => formatString(row.revenues ?? undefined, "money"),
    },
    {
        label: "Net Income",
        sortKey: "netIncomeLoss",
        render: (row: IYFinanceQuarter) => formatString(row.netIncomeLoss ?? undefined, "money"),
    },
    {
        label: "Operating Income",
        sortKey: "operatingIncomeLoss",
        render: (row: IYFinanceQuarter) => formatString(row.operatingIncomeLoss ?? undefined, "money"),
    },
    {
        label: "Gross Profit",
        sortKey: "grossProfit",
        render: (row: IYFinanceQuarter) => formatString(row.grossProfit ?? undefined, "money"),
    },
    {
        label: "EPS Basic",
        sortKey: "earningsPerShareBasic",
        render: (row: IYFinanceQuarter) =>
            row.earningsPerShareBasic != null ? `$${row.earningsPerShareBasic.toFixed(2)}` : "—",
    },
    {
        label: "EPS Diluted",
        sortKey: "earningsPerShareDiluted",
        render: (row: IYFinanceQuarter) =>
            row.earningsPerShareDiluted != null ? `$${row.earningsPerShareDiluted.toFixed(2)}` : "—",
    },
    {
        label: "Assets",
        sortKey: "assets",
        render: (row: IYFinanceQuarter) => formatString(row.assets ?? undefined, "money"),
    },
    {
        label: "Liabilities",
        sortKey: "liabilities",
        render: (row: IYFinanceQuarter) => formatString(row.liabilities ?? undefined, "money"),
    },
    {
        label: "Equity",
        sortKey: "stockholdersEquity",
        render: (row: IYFinanceQuarter) => formatString(row.stockholdersEquity ?? undefined, "money"),
    },
    {
        label: "Cash",
        sortKey: "cashAndCashEquivalentsAtCarryingValue",
        render: (row: IYFinanceQuarter) =>
            formatString(row.cashAndCashEquivalentsAtCarryingValue ?? undefined, "money"),
    },
    {
        label: "Receivables",
        sortKey: "accountsReceivableNetCurrent",
        render: (row: IYFinanceQuarter) =>
            formatString(row.accountsReceivableNetCurrent ?? undefined, "money"),
    },
    {
        label: "Inventory",
        sortKey: "inventoryNet",
        render: (row: IYFinanceQuarter) => formatString(row.inventoryNet ?? undefined, "money"),
    },
    {
        label: "OCF",
        sortKey: "netCashProvidedByUsedInOperatingActivities",
        render: (row: IYFinanceQuarter) =>
            formatString(row.netCashProvidedByUsedInOperatingActivities ?? undefined, "money"),
    },
    {
        label: "Dividends",
        sortKey: "paymentsOfDividends",
        render: (row: IYFinanceQuarter) => formatString(row.paymentsOfDividends ?? undefined, "money"),
    },
    {
        label: "Buybacks",
        sortKey: "paymentsForRepurchaseOfCommonStock",
        render: (row: IYFinanceQuarter) =>
            formatString(row.paymentsForRepurchaseOfCommonStock ?? undefined, "money"),
    },
];

export default function YFinanceQuartersTable({ ticker }: { ticker: string }) {
    const { data, isLoading, error } = useGetDjangoQuartersQuery(ticker);

    return (
        <Row className="mt-4">
            <Col md={12}>
                <Card className="shadow-sm border-0">
                    <Card.Header as="h5" className="bg-dark text-white">
                        YFinance Quarterly Data
                    </Card.Header>
                    <Card.Body>
                        <SortableTable
                            data={data || []}
                            columns={columns}
                            initialSortKey="year"
                            isLoading={isLoading}
                            errors={[error]}
                        />
                    </Card.Body>
                </Card>
            </Col>
        </Row>
    );
}
