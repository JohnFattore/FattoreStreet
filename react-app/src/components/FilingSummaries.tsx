import { Card, Row, Col, Spinner, Alert } from "react-bootstrap";
import { useGetFilingSummariesQuery } from "../functions/api";

export default function FilingSummaries({ ticker }: { ticker: string }) {
    const { data, isLoading, error } = useGetFilingSummariesQuery(ticker);

    return (
        <Row>
            <Col md={12}>
                <Card>
                    <Card.Header as="h5" className="bg-dark text-white">
                        10-K Filing Summaries
                    </Card.Header>
                    <Card.Body>
                        {isLoading ? (
                            <div className="text-center"><Spinner animation="border" /></div>
                        ) : error ? (
                            <Alert variant="danger">Error loading filing summaries.</Alert>
                        ) : !data || data.length === 0 ? (
                            <Alert variant="info">No filing summaries available for {ticker}.</Alert>
                        ) : (
                            data.map((fs) => (
                                <Card key={fs.accessionNumber} className="mb-3">
                                    <Card.Header>
                                        <strong>{fs.filingDate}</strong>
                                        <span className="ms-3 text-muted" style={{ fontFamily: "monospace", fontSize: "0.85em" }}>
                                            {fs.accessionNumber}
                                        </span>
                                    </Card.Header>
                                    <Card.Body>
                                        <p className="mb-0">{fs.summary}</p>
                                    </Card.Body>
                                </Card>
                            ))
                        )}
                    </Card.Body>
                </Card>
            </Col>
        </Row>
    );
}
