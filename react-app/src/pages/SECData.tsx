import { useParams, useNavigate } from "react-router-dom";
import { useGetSecEdgarDataQuery, useGetSecQuartersQuery } from "../functions/api";
import { Alert, Spinner, Card, Container, Row, Col, Button } from "react-bootstrap";
import { formatString } from "../functions/helperFunctions";
import { SortableTable } from "../components/SortableTable";
import { IQuarter } from "../interfaces";
import YFinanceQuartersTable from "../components/YFinanceQuartersTable";
import QuarterlyComparison from "../components/QuarterlyComparison";
import FilingSummaries from "../components/FilingSummaries";
import CompanyOverview from "../components/CompanyOverview";
import TTMIncomeStatement from "../components/TTMIncomeStatement";
import LatestBalanceSheet from "../components/LatestBalanceSheet";
import FinancialRatios from "../components/FinancialRatios";

export default function SECData() {
    const { ticker } = useParams<{ ticker: string }>();
    const navigate = useNavigate();
    const { data, isLoading, error } = useGetSecEdgarDataQuery(ticker || "");
    const { data: quartersData, isLoading: quartersLoading, error: quartersError } = useGetSecQuartersQuery(ticker || "");

    const columns = [
        {
            label: "Period",
            sortKey: "year",
            render: (row: IQuarter) => `${row.year} ${row.quarter}`,
        },
        {
            label: "Period End",
            sortKey: "periodEnd",
            render: (row: IQuarter) => row.periodEnd,
        },
        {
            label: "Revenue",
            sortKey: "revenues",
            render: (row: IQuarter) => formatString(row.revenues, "money"),
        },
        {
            label: "Net Income",
            sortKey: "netIncomeLoss",
            render: (row: IQuarter) => formatString(row.netIncomeLoss, "money"),
        },
        {
            label: "Gross Profit",
            sortKey: "grossProfit",
            render: (row: IQuarter) => formatString(row.grossProfit, "money"),
        },
        {
            label: "Assets",
            sortKey: "assets",
            render: (row: IQuarter) => formatString(row.assets, "money"),
        },
        {
            label: "Liabilities",
            sortKey: "liabilities",
            render: (row: IQuarter) => formatString(row.liabilities, "money"),
        },
        {
            label: "Equity",
            sortKey: "equity",
            render: (row: IQuarter) => formatString(row.equity, "money"),
        },
        {
            label: "OCF",
            sortKey: "ocf",
            render: (row: IQuarter) => formatString(row.ocf, "money"),
        },
    ];

    if (isLoading) {
        return (
            <div className="sec-data-page">
                <Container>
                    <Spinner animation="border" />
                    <p>Loading SEC data for {ticker}...</p>
                </Container>
            </div>
        );
    }

    if (error || !data) {
        return (
            <div className="sec-data-page">
                <Container>
                    <Alert variant="danger">
                        Error loading SEC data for {ticker}. Please try again later.
                    </Alert>
                    <Button onClick={() => navigate(-1)}>Back</Button>
                </Container>
            </div>
        );
    }

    return (
        <div className="sec-data-page">
            <Container>
                <Row>
                    <Col>
                        <h1>SEC EDGAR Data: {data.ticker}</h1>
                    </Col>
                    <Col>
                        <Button variant="outline-secondary" onClick={() => navigate(-1)}>Back</Button>
                    </Col>
                </Row>

                <CompanyOverview data={data} />

                <Row>
                    <Col md={6}>
                        <TTMIncomeStatement data={data} />
                    </Col>
                    <Col md={6}>
                        <LatestBalanceSheet data={data} />
                    </Col>
                </Row>

                <FinancialRatios data={data} />

                <Row>
                    <Col md={12}>
                        <Card>
                            <Card.Header as="h5" className="bg-dark text-white">Historical Quarterly Data</Card.Header>
                            <Card.Body>
                                <SortableTable
                                    data={quartersData?.quarters || []}
                                    columns={columns}
                                    initialSortKey="periodEnd"
                                    initialSortDirection="desc"
                                    isLoading={quartersLoading}
                                    errors={[quartersError]}
                                />
                            </Card.Body>
                        </Card>
                    </Col>
                </Row>

                <YFinanceQuartersTable ticker={ticker || ""} />

                <QuarterlyComparison ticker={ticker || ""} />

                <FilingSummaries ticker={ticker || ""} />

                <Alert variant="info">
                    This data is fetched directly from the SEC EDGAR database via the Fattore Street API.
                </Alert>
            </Container>
        </div>
    );
}
