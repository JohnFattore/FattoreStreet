import ChatbotForm from "../components/ChatbotForm";
import ChatbotOutput from "../components/ChatbotOutput";
import { useSelector } from "react-redux";
import { RootState } from '../main';
import Principles from "../components/Principles";
import LoginRequired from "../components/LoginRequired";

export default function Chatbot() {
    const { access } = useSelector((state: RootState) => state.user);

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
