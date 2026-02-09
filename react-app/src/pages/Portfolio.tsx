import { useState } from "react";
import CreateAccountForm from "../components/CreateAccountForm";
import AccountList from "../components/AccountList";
import YahooFinanceBanner from "../components/YahooFinanceBanner";
import FinnhubBanner from "../components/FinnhubBanner";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import LoginModal from "../components/LoginModal";
import { Alert, Container, Row, Col, Button } from "react-bootstrap";

export default function Portfolio() {
  const { access } = useSelector((state: RootState) => state.user);
  const [showLogin, setShowLogin] = useState(true);

  if (!access) {
    return (
      <Container className="mt-5 text-center">
        <Row className="justify-content-center">
          <Col md={8}>
            <Alert variant="info" className="p-5 shadow-sm">
              <h2 className="mb-4">Protected Area</h2>
              <p className="lead mb-4">You must be logged in to view and manage your portfolio.</p>
              <Button variant="primary" size="lg" onClick={() => setShowLogin(true)}>
                Sign In to Continue
              </Button>
            </Alert>
          </Col>
        </Row>
        <LoginModal show={showLogin} onHide={() => setShowLogin(false)} />
      </Container>
    );
  }

  return (
    <>
      <AccountList />
      <CreateAccountForm />
      <YahooFinanceBanner />
      <FinnhubBanner />
    </>
  );
}
