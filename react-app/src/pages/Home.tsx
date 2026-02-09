import { Container, Row, Col, Card, Button } from "react-bootstrap";
import { Link } from "react-router-dom";

export default function Home() {
    return (
        <div className="home-page">
            {/* Hero Section */}
            <div
                className="hero-section text-center py-5 mb-5"
                style={{
                    background: "radial-gradient(circle at top left, var(--quaternary), var(--tertiary))",
                    color: "white",
                    borderRadius: "15px",
                    minHeight: "400px",
                    display: "flex",
                    flexDirection: "column",
                    justifyContent: "center",
                    alignItems: "center",
                }}
            >
                <h1 className="display-3 fw-bold mb-3">Master Your Financial Future</h1>
                <p className="lead mb-4 px-3" style={{ maxWidth: '800px' }}>
                    Welcome to Fattore Street. We empower investors with the knowledge and tools to harness the power of low-cost index funds and smart portfolio management.
                </p>
                <div className="d-flex gap-3">
                    <Button as={Link as any} to="/portfolio" variant="primary" size="lg">Get Started</Button>
                    <Button as={Link as any} to="/visualizer" variant="outline-light" size="lg">Explore Data</Button>
                </div>
            </div>

            {/* Testimonial Quote */}
            <Container className="mb-5">
                <blockquote className="blockquote text-center py-4 px-3" style={{ borderLeft: 'none' }}>
                    <p className="mb-4 font-italic fs-4">
                        "By periodically investing in an index fund, for example, the know-nothing investor can actually out-perform most investment professionals. Paradoxically, when ‘dumb’ money acknowledges its limitations, it ceases to be dumb."
                    </p>
                    <footer className="blockquote-footer fs-5">
                        Warren Buffett, <cite title="Source Title">Chairman, Berkshire Hathaway</cite>
                    </footer>
                </blockquote>
            </Container>

            {/* Features Grid */}
            <Container className="mb-5">
                <h2 className="text-center mb-5 fw-bold">Platform Capabilities</h2>
                <Row className="g-4">
                    <Col md={4}>
                        <Card className="h-100 shadow-sm transition-hover">
                            <Card.Body>
                                <div className="fs-1 mb-3 text-primary">📊</div>
                                <Card.Title className="fw-bold">Portfolio Management</Card.Title>
                                <Card.Text>
                                    A professional stock and bond paper trader. Create hypothetical portfolios, track performance, and master the art of disciplined investing without capital risk.
                                </Card.Text>
                                <Button as={Link as any} to="/portfolio" variant="link" className="p-0 text-decoration-none">Launch Portfolio &rarr;</Button>
                            </Card.Body>
                        </Card>
                    </Col>
                    <Col md={4}>
                        <Card className="h-100 shadow-sm transition-hover">
                            <Card.Body>
                                <div className="fs-1 mb-3 text-primary">📈</div>
                                <Card.Title className="fw-bold">Market Visualizer</Card.Title>
                                <Card.Text>
                                    Analyze how your chosen assets compare to the broader market index. Insights into diversification, weighting, and historical performance comparisons.
                                </Card.Text>
                                <Button as={Link as any} to="/visualizer" variant="link" className="p-0 text-decoration-none">View Insights &rarr;</Button>
                            </Card.Body>
                        </Card>
                    </Col>
                    <Col md={4}>
                        <Card className="h-100 shadow-sm transition-hover">
                            <Card.Body>
                                <div className="fs-1 mb-3 text-primary">🔔</div>
                                <Card.Title className="fw-bold">Live Watchlist</Card.Title>
                                <Card.Text>
                                    Keep your finger on the pulse with a live stock and ETF tracker. Monitor real-time price movements and market trends for your favorite securities.
                                </Card.Text>
                                <Button as={Link as any} to="/watchlist" variant="link" className="p-0 text-decoration-none">Monitor Markets &rarr;</Button>
                            </Card.Body>
                        </Card>
                    </Col>
                    <Col md={4}>
                        <Card className="h-100 shadow-sm transition-hover">
                            <Card.Body>
                                <div className="fs-1 mb-3 text-primary">🌎</div>
                                <Card.Title className="fw-bold">Macro Analytics</Card.Title>
                                <Card.Text>
                                    Understand the big picture with deep-dive graphs of essential macroeconomic data. See how broader economic indicators affect the investment landscape.
                                </Card.Text>
                                <Button as={Link as any} to="/economic-indicators" variant="link" className="p-0 text-decoration-none">Study Macro &rarr;</Button>
                            </Card.Body>
                        </Card>
                    </Col>
                    <Col md={4}>
                        <Card className="h-100 shadow-sm transition-hover">
                            <Card.Body>
                                <div className="fs-1 mb-3 text-primary">🤖</div>
                                <Card.Title className="fw-bold">Boglehead AI</Card.Title>
                                <Card.Text>
                                    Chat with our intelligent bot trained on Boglehead principles. Get immediate answers to your index fund questions and learn about long-term wealth building.
                                </Card.Text>
                                <Button as={Link as any} to="/chatbot" variant="link" className="p-0 text-decoration-none">Chat with AI &rarr;</Button>
                            </Card.Body>
                        </Card>
                    </Col>
                    <Col md={4}>
                        <Card className="h-100 shadow-sm transition-hover">
                            <Card.Body>
                                <div className="fs-1 mb-3 text-primary">🍔</div>
                                <Card.Title className="fw-bold">Nashville Restaurants</Card.Title>
                                <Card.Text>
                                    Because life isn't just about investing. Explore our curated Nashville restaurant recommender for the best dining experiences in Music City.
                                </Card.Text>
                                <Button as={Link as any} to="/restaurants" variant="link" className="p-0 text-decoration-none">Find Dining &rarr;</Button>
                            </Card.Body>
                        </Card>
                    </Col>
                </Row>
            </Container>

            {/* Footer Note */}
            <div className="mt-5 text-center text-muted border-top pt-4">
                <p>New to index fund investing? <Link to="/user">Create an account</Link> to start building your portfolio today.</p>
            </div>
        </div>
    );
}