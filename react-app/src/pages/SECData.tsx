import { useParams, useNavigate } from "react-router-dom";
import { useGetSecEdgarDataQuery, useGetSecQuartersQuery } from "../functions/api";
import { Alert, Spinner, Card, Container, Row, Col, Button, Table } from "react-bootstrap";
import { formatString } from "../functions/helperFunctions";
import { SortableTable } from "../components/SortableTable";
import { IQuarter } from "../interfaces";
import YFinanceQuartersTable from "../components/YFinanceQuartersTable";
import QuarterlyComparison from "../components/QuarterlyComparison";
import FilingSummaries from "../components/FilingSummaries";

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

                <Row>
                    <Col md={12}>
                        <Card>
                            <Card.Header as="h5" className="bg-primary text-white">Company Overview</Card.Header>
                            <Card.Body>
                                <Row>
                                    <Col md={4}>
                                        <p>Ticker</p>
                                        <h4>{data.ticker}</h4>
                                    </Col>
                                    <Col md={4}>
                                        <p>CIK</p>
                                        <h4>{data.cik}</h4>
                                    </Col>
                                    <Col md={4}>
                                        <p>Latest Quarter End</p>
                                        <h4>{data.latestQuarterEnd}</h4>
                                    </Col>
                                </Row>
                            </Card.Body>
                        </Card>
                    </Col>
                </Row>

                <Row>
                    <Col md={6}>
                        <Card>
                            <Card.Header as="h5" className="bg-success text-white">TTM Income Statement</Card.Header>
                            <Card.Body>
                                <Table hover size="sm">
                                    <tbody>
                                        <tr>
                                            <td><strong>TTM Revenue</strong></td>
                                            <td>{formatString(Number(data.ttmRevenue), "money")}</td>
                                        </tr>
                                        <tr>
                                            <td><strong>TTM Revenue YoY Growth</strong></td>
                                            <td className={parseFloat(data.ttmRevenueYoY) >= 0 ? "text-success" : "text-danger"}>
                                                {data.ttmRevenueYoY}
                                            </td>
                                        </tr>
                                        <tr>
                                            <td><strong>TTM Gross Profit</strong></td>
                                            <td>{formatString(Number(data.ttmGrossProfit), "money")}</td>
                                        </tr>
                                        <tr>
                                            <td><strong>TTM Operating Income</strong></td>
                                            <td>{formatString(Number(data.ttmOperatingIncome), "money")}</td>
                                        </tr>
                                        <tr>
                                            <td><strong>TTM Net Income</strong></td>
                                            <td>{formatString(Number(data.ttmNetIncome), "money")}</td>
                                        </tr>
                                        <tr>
                                            <td><strong>TTM Net Income YoY Growth</strong></td>
                                            <td className={parseFloat(data.ttmNetIncomeYoY) >= 0 ? "text-success" : "text-danger"}>
                                                {data.ttmNetIncomeYoY}
                                            </td>
                                        </tr>
                                        <tr>
                                            <td><strong>TTM Operating Cash Flow</strong></td>
                                            <td>{formatString(Number(data.ttmOperatingCashFlow), "money")}</td>
                                        </tr>
                                    </tbody>
                                </Table>
                            </Card.Body>
                        </Card>
                    </Col>

                    <Col md={6}>
                        <Card>
                            <Card.Header as="h5" className="bg-info text-white">Latest Balance Sheet</Card.Header>
                            <Card.Body>
                                <Table hover size="sm">
                                    <tbody>
                                        <tr>
                                            <td><strong>Total Assets</strong></td>
                                            <td>{formatString(Number(data.latestAssets), "money")}</td>
                                        </tr>
                                        <tr>
                                            <td><strong>Total Liabilities</strong></td>
                                            <td>{formatString(Number(data.latestLiabilities), "money")}</td>
                                        </tr>
                                        <tr>
                                            <td><strong>Total Equity</strong></td>
                                            <td>{formatString(Number(data.latestEquity), "money")}</td>
                                        </tr>
                                        <tr>
                                            <td><strong>Cash & Equivalents</strong></td>
                                            <td>{formatString(Number(data.latestCash), "money")}</td>
                                        </tr>
                                        <tr>
                                            <td><strong>Inventory</strong></td>
                                            <td>{formatString(Number(data.latestInventory), "money")}</td>
                                        </tr>
                                        <tr>
                                            <td><strong>Latest EPS</strong></td>
                                            <td>${data.latestEps}</td>
                                        </tr>
                                    </tbody>
                                </Table>
                            </Card.Body>
                        </Card>
                    </Col>
                </Row>

                <Row>
                    <Col md={12}>
                        <Card>
                            <Card.Header as="h5" className="bg-warning text-dark">Financial Ratios & Metrics</Card.Header>
                            <Card.Body>
                                <Row>
                                    <Col md={4}>
                                        <Table hover size="sm">
                                            <tbody>
                                                <tr>
                                                    <td><strong>Net Margin</strong></td>
                                                    <td>{data.netMargin}</td>
                                                </tr>
                                                <tr>
                                                    <td><strong>Gross Margin</strong></td>
                                                    <td>{data.grossMargin}</td>
                                                </tr>
                                            </tbody>
                                        </Table>
                                    </Col>
                                    <Col md={4}>
                                        <Table hover size="sm">
                                            <tbody>
                                                <tr>
                                                    <td><strong>Return on Assets (ROA)</strong></td>
                                                    <td>{data.roA}</td>
                                                </tr>
                                                <tr>
                                                    <td><strong>Debt to Assets</strong></td>
                                                    <td>{data.debtToAssets}</td>
                                                </tr>
                                            </tbody>
                                        </Table>
                                    </Col>
                                    <Col md={4}>
                                        <Table hover size="sm">
                                            <tbody>
                                                <tr>
                                                    <td><strong>Cash to Liabilities</strong></td>
                                                    <td>{data.cashToLiabilities}</td>
                                                </tr>
                                                <tr>
                                                    <td><strong>OCF to Net Income</strong></td>
                                                    <td>{data.ocfToNetIncome}</td>
                                                </tr>
                                            </tbody>
                                        </Table>
                                    </Col>
                                </Row>
                            </Card.Body>
                        </Card>
                    </Col>
                </Row>

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
