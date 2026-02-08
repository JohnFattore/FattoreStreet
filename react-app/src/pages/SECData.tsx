import { useParams, useNavigate } from "react-router-dom";
import { useGetSecEdgarDataQuery } from "../functions/api";
import { Alert, Spinner, Card, Container, Row, Col, Button, Table } from "react-bootstrap";
import { formatString } from "../functions/helperFunctions";

export default function SECData() {
    const { ticker } = useParams<{ ticker: string }>();
    const navigate = useNavigate();
    const { data, isLoading, error } = useGetSecEdgarDataQuery(ticker || "");

    if (isLoading) {
        return (
            <Container className="mt-4 text-center">
                <Spinner animation="border" />
                <p>Loading SEC data for {ticker}...</p>
            </Container>
        );
    }

    if (error || !data) {
        return (
            <Container className="mt-4">
                <Alert variant="danger">
                    Error loading SEC data for {ticker}. Please try again later.
                </Alert>
                <Button onClick={() => navigate(-1)}>Back</Button>
            </Container>
        );
    }

    return (
        <Container className="mt-4">
            <Row className="mb-4 align-items-center">
                <Col>
                    <h1>SEC EDGAR Data: {data.ticker}</h1>
                </Col>
                <Col className="text-end">
                    <Button variant="outline-secondary" onClick={() => navigate(-1)}>Back</Button>
                </Col>
            </Row>

            <Row>
                <Col md={6}>
                    <Card className="mb-4 shadow-sm">
                        <Card.Header as="h5">Company Information</Card.Header>
                        <Card.Body>
                            <Table striped bordered hover size="sm">
                                <tbody>
                                    <tr>
                                        <td><strong>Ticker</strong></td>
                                        <td>{data.ticker}</td>
                                    </tr>
                                    <tr>
                                        <td><strong>CIK</strong></td>
                                        <td>{data.cik}</td>
                                    </tr>
                                    <tr>
                                        <td><strong>Latest Quarter End</strong></td>
                                        <td>{data.latestQuarterEnd}</td>
                                    </tr>
                                </tbody>
                            </Table>
                        </Card.Body>
                    </Card>
                </Col>

                <Col md={6}>
                    <Card className="mb-4 shadow-sm">
                        <Card.Header as="h5">TTM Financials</Card.Header>
                        <Card.Body>
                            <Table striped bordered hover size="sm">
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
                                        <td><strong>TTM Net Income</strong></td>
                                        <td>{formatString(Number(data.ttmNetIncome), "money")}</td>
                                    </tr>
                                    <tr>
                                        <td><strong>TTM Net Income YoY Growth</strong></td>
                                        <td className={parseFloat(data.ttmNetIncomeYoY) >= 0 ? "text-success" : "text-danger"}>
                                            {data.ttmNetIncomeYoY}
                                        </td>
                                    </tr>
                                </tbody>
                            </Table>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>

            <Alert variant="info" className="mt-4">
                This data is fetched directly from the SEC EDGAR database via the Fattore Street API.
            </Alert>
        </Container>
    );
}
