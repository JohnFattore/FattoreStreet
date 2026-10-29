import { useState } from "react";
import { Col, Row } from "react-bootstrap";
import ChatbotForm from "../components/ChatbotForm";
import ChatbotOutput from "../components/ChatbotOutput";
import ConversationList from "../components/ConversationList";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import Principles from "../components/Principles";
import LoginRequired from "../components/LoginRequired";

export default function Chatbot() {
  const { access } = useSelector((state: RootState) => state.user);
  // null means a new conversation that hasn't been asked anything yet
  const [conversationId, setConversationId] = useState<number | null>(null);

  const renderChatbot = () => {
    if (!access) {
      return (
        <LoginRequired
          title="Boglehead Insights Await"
          message="Log in to ask our AI chatbot about Boglehead investment principles."
          buttonText="Sign In to Chat"
          defaultShowLogin={false}
          alertClassName="p-4 shadow-sm theme-protected-box"
        />
      );
    }
    return (
      <Row>
        <Col md={3}>
          <ConversationList
            selectedId={conversationId}
            onSelect={setConversationId}
          />
        </Col>
        <Col md={9}>
          <ChatbotOutput conversationId={conversationId} />
          <ChatbotForm
            conversationId={conversationId}
            onConversationCreated={setConversationId}
          />
        </Col>
      </Row>
    );
  };

  return (
    <>
      <h2>Boglehead Chatbot</h2>
      {renderChatbot()}
      <Principles />
    </>
  );
}
