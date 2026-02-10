import { useSelector } from "react-redux";
import { RootState } from "../main";
import LoginForm from "../components/LoginForm";
import RegisterForm from "../components/RegisterForm";
import { Container, Row, Col, Card, ListGroup } from "react-bootstrap";

export default function User() {
    const { username, access, darkMode } = useSelector((state: RootState) => state.user);

    if (!access) {
        return (
            <Container className="mt-5">
                <Row className="justify-content-center">
                    <Col md={6}>
                        <Card className="shadow-sm border-0 mb-4">
                            <Card.Header as="h5" className="theme-header-quaternary">
                                Sign In
                            </Card.Header>
                            <Card.Body>
                                <LoginForm />
                            </Card.Body>
                        </Card>

                        <Card className="shadow-sm border-0">
                            <Card.Header as="h5" className="theme-header-quaternary">
                                Create User
                            </Card.Header>
                            <Card.Body>
                                <RegisterForm />
                            </Card.Body>
                        </Card>
                    </Col>
                </Row>
            </Container>
        );
    }

    return (
        <Container className="mt-5">
            <Row className="justify-content-center">
                <Col md={8}>
                    <Card className="shadow-sm border-0">
                        <Card.Header as="h5" className="theme-header-quaternary">
                            User Profile
                        </Card.Header>
                        <Card.Body>
                            <div className="text-center mb-4">
                                <div className="bg-light rounded-circle d-inline-flex align-items-center justify-content-center" style={{ width: '100px', height: '100px' }}>
                                    <h1 className="display-4 mb-0 text-primary">{username.charAt(0).toUpperCase()}</h1>
                                </div>
                                <h2 className="mt-3">{username}</h2>
                                <p className="text-muted">Fattore Street Member</p>
                            </div>

                            <ListGroup variant="flush">
                                <ListGroup.Item className="d-flex justify-content-between align-items-center px-0">
                                    <div>
                                        <h6 className="mb-0">Username</h6>
                                        <small className="text-muted">Unique identifier for your user profile</small>
                                    </div>
                                    <span>{username}</span>
                                </ListGroup.Item>
                                <ListGroup.Item className="d-flex justify-content-between align-items-center px-0">
                                    <div>
                                        <h6 className="mb-0">Theme Preference</h6>
                                        <small className="text-muted">Your current visual mode</small>
                                    </div>
                                    <span className="badge bg-secondary">{darkMode ? "Dark Mode" : "Light Mode"}</span>
                                </ListGroup.Item>
                                <ListGroup.Item className="d-flex justify-content-between align-items-center px-0">
                                    <div>
                                        <h6 className="mb-0">User Status</h6>
                                        <small className="text-muted">Authentication level</small>
                                    </div>
                                    <span className="text-success">Logged In</span>
                                </ListGroup.Item>
                            </ListGroup>
                        </Card.Body>
                        <Card.Footer className="bg-white border-0 text-center pb-4">
                            <p className="text-muted small">Manage your settings and preferences here in the future.</p>
                        </Card.Footer>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
}
