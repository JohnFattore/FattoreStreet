import { useSelector } from "react-redux";
import { RootState } from '../main';
import showdown from "showdown";


function convertMarkdownToHtml(markdownText) {
    const converter = new showdown.Converter();
    return converter.makeHtml(markdownText);
}

export default function ChatbotOutput() {
    const { messages } = useSelector((state: RootState) => state.chatbot);

    return (
        <>
            {messages.map((msg, index) => (
                <div key={index} dangerouslySetInnerHTML={{ __html: convertMarkdownToHtml(msg) }} />
            ))}
        </>
    );
}