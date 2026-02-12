import LoginForm from "../components/LoginForm";
import RegisterForm from "../components/RegisterForm";
import { Container, Row, Col, Card, ListGroup } from "react-bootstrap";
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';
import { Button } from 'react-bootstrap';
import { setUserDarkMode } from '../reducers/userReducer';

export default function User() {
    const { username, access, darkMode } = useSelector((state: RootState) => state.user);
    const dispatch = useDispatch<AppDispatch>();

    if (!access) {
        return (
            <Container>
                <Row className="justify-content-center">
                    <Col md={6}>
                        <Card>
                            <Card.Header as="h5" className="theme-header-quaternary">
                                Sign In
                            </Card.Header>
                            <Card.Body>
                                <LoginForm />
                            </Card.Body>
                        </Card>

                        <Card>
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
        <Container>
            <Row className="justify-content-center">
                <Col md={8}>
                    <Card>
                        <Card.Header as="h5" className="theme-header-quaternary">
                            User Profile
                        </Card.Header>
                        <Card.Body>
                            <div>
                                <h2>{username}</h2>
                                <p>Fattore Street Member</p>
                            </div>

                            <ListGroup variant="flush">
                                <ListGroup.Item className="d-flex justify-content-between align-items-center px-0">
                                    <div>
                                        <h6>Username</h6>
                                        <small>Unique identifier for your user profile</small>
                                    </div>
                                    <span>{username}</span>
                                </ListGroup.Item>
                                <ListGroup.Item className="d-flex justify-content-between align-items-center px-0">
                                    <div>
                                        <h6>Theme Preference</h6>
                                        <small>Your current visual mode</small>
                                    </div>
                                    <Button onClick={() => dispatch(setUserDarkMode(!darkMode))}> {darkMode ? "Light Mode" : "Dark Mode"} </Button>
                                </ListGroup.Item>
                                <ListGroup.Item className="d-flex justify-content-between align-items-center px-0">
                                    <div>
                                        <h6>User Status</h6>
                                        <small>Authentication level</small>
                                    </div>
                                    <span>Logged In</span>
                                </ListGroup.Item>
                            </ListGroup>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
}
