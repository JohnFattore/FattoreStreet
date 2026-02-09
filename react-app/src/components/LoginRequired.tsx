import { useState } from "react";
import { Alert, Button, Col, Container, Row } from "react-bootstrap";
import LoginModal from "./LoginModal";

interface LoginRequiredProps {
  title: string;
  message: string;
  buttonText?: string;
  /**
   * Alert className for theming. Defaults to the app's protected-area styling.
   */
  alertClassName?: string;
  /**
   * Whether the sign-in modal should open by default.
   */
  defaultShowLogin?: boolean;
}

export default function LoginRequired({
  title,
  message,
  buttonText = "Sign In",
  alertClassName = "p-5 shadow-sm theme-protected-box",
  defaultShowLogin = true,
}: LoginRequiredProps) {
  const [showLogin, setShowLogin] = useState(defaultShowLogin);

  return (
    <Container className="mt-5 text-center">
      <Row className="justify-content-center">
        <Col md={8}>
          <Alert variant="light" className={alertClassName}>
            <h2 className="mb-4">{title}</h2>
            <p className="lead mb-4">{message}</p>
            <Button
              variant="primary"
              size="lg"
              onClick={() => setShowLogin(true)}
            >
              {buttonText}
            </Button>
          </Alert>
        </Col>
      </Row>
      <LoginModal show={showLogin} onHide={() => setShowLogin(false)} />
    </Container>
  );
}

