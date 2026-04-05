import { Card, Row, Col } from "react-bootstrap";
import { ISECData } from "../interfaces";

export default function CompanyOverview({ data }: { data: ISECData }) {
    return (
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
    );
}
