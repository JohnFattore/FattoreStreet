import { Container, Row, Col, Card } from "react-bootstrap";
import { Link } from "react-router-dom";

// "/copy" makes Google prompt the visitor to save their own copy instead of
// opening the shared template (which must stay "Anyone with the link: Viewer").
const PORTFOLIO_SHEET_COPY_URL =
  "https://docs.google.com/spreadsheets/d/1gTdUHR769WEa58gfhRSP76oVkD_UiX766xkiaohZCRc/copy";

export default function Home() {
  return (
    <div className="home-page">
      {/* Hero Section */}
      <div
        className="hero-section text-center py-5 mb-5"
        style={{
          background:
            "radial-gradient(circle at top left, var(--quaternary), var(--tertiary))",
          color: "white",
          borderRadius: "15px",
          minHeight: "400px",
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          alignItems: "center",
        }}
      >
        <h1 className="display-3 fw-bold mb-3 text-secondary-theme">
          Master Your Financial Future
        </h1>
        <p
          className="lead mb-4 px-3 text-secondary-theme"
          style={{ maxWidth: "800px" }}
        >
          Welcome to Fattore Street. We empower investors with the knowledge and
          tools to harness the power of low-cost index funds and smart portfolio
          management.
        </p>
        <div className="d-flex gap-3">
          <Link to="/user" className="btn btn-primary btn-lg">
            Get Started
          </Link>
          <Link to="/chatbot" className="btn btn-outline-light btn-lg">
            Chat with AI
          </Link>
          <Link to="/indexes" className="btn btn-outline-light btn-lg">
            Explore Indexes
          </Link>
        </div>
      </div>

      {/* Testimonial Quote */}
      <Container className="mb-5">
        <blockquote
          className="blockquote text-center py-4 px-3"
          style={{ borderLeft: "none" }}
        >
          <p className="mb-4 font-italic fs-4">
            "By periodically investing in an index fund, for example, the
            know-nothing investor can actually out-perform most investment
            professionals. Paradoxically, when ‘dumb’ money acknowledges its
            limitations, it ceases to be dumb."
          </p>
          <footer className="blockquote-footer fs-5">
            Warren Buffett,{" "}
            <cite title="Source Title">Chairman, Berkshire Hathaway</cite>
          </footer>
        </blockquote>
      </Container>

      {/* Features Grid */}
      <Container className="mb-5">
        <h2 className="text-center mb-5 fw-bold">Platform Capabilities</h2>
        <Row className="g-4">
          <Col md={4}>
            <Card>
              <Card.Body>
                <div className="fs-1 mb-3 text-primary">🤖</div>
                <Card.Title>Boglehead AI</Card.Title>
                <Card.Text>
                  Chat with our intelligent bot trained on Boglehead principles.
                  Get immediate answers to your index fund questions and learn
                  about long-term wealth building.
                </Card.Text>
                <Link
                  to="/chatbot"
                  className="btn btn-link p-0 text-decoration-none"
                >
                  Chat with AI &rarr;
                </Link>
              </Card.Body>
            </Card>
          </Col>
          <Col md={4}>
            <Card>
              <Card.Body>
                <div className="fs-1 mb-3 text-primary">🏛️</div>
                <Card.Title>Fattore Indexes</Card.Title>
                <Card.Text>
                  Browse the Fattore 50, 100 and 1000, built entirely from free
                  primary-source SEC and IEX data. See every constituent, its
                  weight and its key metrics.
                </Card.Text>
                <Link
                  to="/indexes"
                  className="btn btn-link p-0 text-decoration-none"
                >
                  Explore Indexes &rarr;
                </Link>
              </Card.Body>
            </Card>
          </Col>
          <Col md={4}>
            <Card>
              <Card.Body>
                <div className="fs-1 mb-3 text-primary">📊</div>
                <Card.Title>Portfolio Management</Card.Title>
                <Card.Text>
                  A stock and bond paper trader. Build hypothetical portfolios,
                  check your allocation against the market, and keep a live
                  watchlist, all without capital risk.
                </Card.Text>
                <Link
                  to="/portfolio"
                  className="btn btn-link p-0 text-decoration-none"
                >
                  Launch Portfolio &rarr;
                </Link>
              </Card.Body>
            </Card>
          </Col>
        </Row>
      </Container>

      {/* Spreadsheet Tracker */}
      <Container className="mb-5">
        <Card className="text-center">
          <Card.Body className="py-4">
            <div className="fs-1 mb-3 text-primary">📋</div>
            <Card.Title as="h2" className="fw-bold">
              Prefer a Spreadsheet?
            </Card.Title>
            <Card.Text className="mx-auto w-75">
              Grab a free Google Sheets portfolio tracker. One click saves your
              own editable copy to your Google Drive, no account here required.
            </Card.Text>
            <a
              href={PORTFOLIO_SHEET_COPY_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="btn btn-primary"
            >
              Copy the Tracker &rarr;
            </a>
            <div className="mt-3">
              <Link
                to="/blog/portfolio-tracker"
                className="btn btn-link p-0 text-decoration-none"
              >
                How to get started
              </Link>
            </div>
          </Card.Body>
        </Card>
      </Container>

      {/* Footer Note */}
      <div className="mt-5 text-center text-muted border-top pt-4">
        <p>
          New to index fund investing? <Link to="/user">Create a user</Link> to
          start building your portfolio today.
        </p>
      </div>
    </div>
  );
}
