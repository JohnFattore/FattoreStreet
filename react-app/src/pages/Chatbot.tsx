import ChatbotForm from "../components/ChatbotForm";
import ChatbotOutput from "../components/ChatbotOutput";
import { useSelector } from "react-redux";
import { RootState } from '../main';
import LoginForm from "../components/LoginForm";
import Principles from "../components/Principles";

export default function Chatbot() {
    const { access } = useSelector((state: RootState) => state.user);

    const renderChatbot = () => {

        if (!access) {
            return (<LoginForm/>)
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
            <Principles/>
        </>
    );
}