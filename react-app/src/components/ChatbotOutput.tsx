import { useSelector } from "react-redux";
import { RootState } from '../main';


export default function ChatbotOutput() {
    const { messages } = useSelector((state: RootState) => state.chatbot);

    return (
        <>
            {messages.map((msg, index) => (
                <p key={index}>{msg}</p>
            ))}
        </>
    );
}