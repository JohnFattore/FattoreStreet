import { Card, Row, Col, Table } from "react-bootstrap";
import { ISECData } from "../interfaces";

export default function FinancialRatios({ data }: { data: ISECData }) {
    return (
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
    );
}
