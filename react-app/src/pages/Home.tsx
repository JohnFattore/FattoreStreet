import { Container, Row, Col, Card, Button } from "react-bootstrap";
import { Link } from "react-router-dom";

export default function Home() {
    return (
        <div className="home-page">
            {/* Hero Section */}
            <div className="hero-section">
                <h1 className="text-secondary-theme">Master Your Financial Future</h1>
                <p className="text-secondary-theme">
                    Welcome to Fattore Street. We empower investors with the knowledge and tools to harness the power of low-cost index funds and smart portfolio management.
                </p>
                <div>
                    <Button as={Link as any} to="/user" variant="primary" size="lg">Get Started</Button>
                    <Button as={Link as any} to="/watchlist" variant="outline-light" size="lg">Explore Data</Button>
                    <Button as={Link as any} to="/chatbot" variant="outline-light" size="lg">Chat with AI</Button>
                </div>
            </div>

            {/* Testimonial Quote */}
            <Container>
                <blockquote style={{ borderLeft: 'none' }}>
                    <p>
                        "By periodically investing in an index fund, for example, the know-nothing investor can actually out-perform most investment professionals. Paradoxically, when 'dumb' money acknowledges its limitations, it ceases to be dumb."
                    </p>
                    <footer className="blockquote-footer">
                        Warren Buffett, <cite title="Source Title">Chairman, Berkshire Hathaway</cite>
                    </footer>
                </blockquote>
            </Container>

            {/* Features Grid */}
            <Container>
                <h2>Platform Capabilities</h2>
                <Row>
                    <Col md={4}>
                        <Card>
                            <Card.Body>
                                <div>📊</div>
                                <Card.Title>Portfolio Management</Card.Title>
                                <Card.Text>
                                    A professional stock and bond paper trader. Create hypothetical portfolios, track performance, and master the art of disciplined investing without capital risk.
                                </Card.Text>
                                <Button as={Link as any} to="/portfolio" variant="link">Launch Portfolio &rarr;</Button>
                            </Card.Body>
                        </Card>
                    </Col>
                    <Col md={4}>
                        <Card>
                            <Card.Body>
                                <div>📈</div>
                                <Card.Title>Market Visualizer</Card.Title>
                                <Card.Text>
                                    Analyze how your chosen assets compare to the broader market index. Insights into diversification, weighting, and historical performance comparisons.
                                </Card.Text>
                                <Button as={Link as any} to="/visualizer" variant="link">View Insights &rarr;</Button>
                            </Card.Body>
                        </Card>
                    </Col>
                    <Col md={4}>
                        <Card>
                            <Card.Body>
                                <div>🔔</div>
                                <Card.Title>Live Watchlist</Card.Title>
                                <Card.Text>
                                    Keep your finger on the pulse with a live stock and ETF tracker. Monitor real-time price movements and market trends for your favorite securities.
                                </Card.Text>
                                <Button as={Link as any} to="/watchlist" variant="link">Monitor Markets &rarr;</Button>
                            </Card.Body>
                        </Card>
                    </Col>
                    <Col md={4}>
                        <Card>
                            <Card.Body>
                                <div>🌎</div>
                                <Card.Title>Macro Analytics</Card.Title>
                                <Card.Text>
                                    Understand the big picture with deep-dive graphs of essential macroeconomic data. See how broader economic indicators affect the investment landscape.
                                </Card.Text>
                                <Button as={Link as any} to="/economic-indicators" variant="link">Study Macro &rarr;</Button>
                            </Card.Body>
                        </Card>
                    </Col>
                    <Col md={4}>
                        <Card>
                            <Card.Body>
                                <div>🤖</div>
                                <Card.Title>Boglehead AI</Card.Title>
                                <Card.Text>
                                    Chat with our intelligent bot trained on Boglehead principles. Get immediate answers to your index fund questions and learn about long-term wealth building.
                                </Card.Text>
                                <Button as={Link as any} to="/chatbot" variant="link">Chat with AI &rarr;</Button>
                            </Card.Body>
                        </Card>
                    </Col>
                    <Col md={4}>
                        <Card>
                            <Card.Body>
                                <div>🍔</div>
                                <Card.Title>Nashville Restaurants</Card.Title>
                                <Card.Text>
                                    Because life isn't just about investing. Explore our curated Nashville restaurant recommender for the best dining experiences in Music City.
                                </Card.Text>
                                <Button as={Link as any} to="/restaurants" variant="link">Find Dining &rarr;</Button>
                            </Card.Body>
                        </Card>
                    </Col>
                </Row>
            </Container>

            {/* Footer Note */}
            <div>
                <p>New to index fund investing? <Link to="/user">Create a user</Link> to start building your portfolio today.</p>
            </div>
        </div>
    );
}
