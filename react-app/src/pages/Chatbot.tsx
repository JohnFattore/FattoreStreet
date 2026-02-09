import { useState, useEffect } from "react";
import ChatbotForm from "../components/ChatbotForm";
import ChatbotOutput from "../components/ChatbotOutput";
import { useSelector, useDispatch } from "react-redux";
import { RootState, AppDispatch } from '../main';
import LoginModal from "../components/LoginModal";
import Principles from "../components/Principles";
import { getChatbot } from "../functions/axiosFunctions";
import { Button, Alert, Container, Row, Col } from "react-bootstrap";

export default function Chatbot() {
    const { access } = useSelector((state: RootState) => state.user);
    const dispatch = useDispatch<AppDispatch>();
    const [showLogin, setShowLogin] = useState(false);

    useEffect(() => {
        if (access) {
            dispatch(getChatbot());
        }
    }, [access, dispatch]);

    const renderChatbot = () => {
        if (!access) {
            return (
                <Container className="my-4 text-center">
                    <Row className="justify-content-center">
                        <Col md={8}>
                            <Alert variant="info" className="p-4 shadow-sm">
                                <h4 className="mb-3">Boglehead Insights Await</h4>
                                <p className="mb-4">Log in to ask our AI chatbot about Boglehead investment principles.</p>
                                <Button variant="primary" onClick={() => setShowLogin(true)}>
                                    Sign In to Chat
                                </Button>
                            </Alert>
                        </Col>
                    </Row>
                    <LoginModal show={showLogin} onHide={() => setShowLogin(false)} />
                </Container>
            );
        }
        return <>
            <ChatbotOutput />
            <ChatbotForm />
        </>;
    };

    return (
        <>
            <h2>Boglehead Chatbot</h2>
            {renderChatbot()}
            <Principles />
        </>
    );
}