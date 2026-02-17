import { useSelector } from "react-redux";
import { RootState } from '../main';
import { IChatMessage } from '../interfaces';
import showdown from "showdown";
import DOMPurify from "dompurify";

function convertMarkdownToHtml(markdownText: string) {
    if (!markdownText) return "";
    const text = String(markdownText);
    const converter = new showdown.Converter();
    return DOMPurify.sanitize(converter.makeHtml(text));
}

export default function ChatbotOutput() {
    const { messages } = useSelector((state: RootState) => state.chatbot);

    return (
        <div className="overflow-auto mb-4 d-flex flex-column" style={{ maxHeight: '600px' }}>
            {messages.map((msg: IChatMessage, index: number) => (
                <div key={index} className={`p-2 rounded-3 m-1 ${msg.role === 'user' ? 'align-self-end' : 'align-self-start'}`} style={{
                    backgroundColor: msg.role === 'user' ? 'var(--primary)' : 'var(--tertiary)',
                    color: msg.role === 'user' ? 'var(--secondary)' : 'var(--primary)',
                    maxWidth: '80%'
                }}>
                    {msg.role === 'model' ? (
                        <div dangerouslySetInnerHTML={{ __html: convertMarkdownToHtml(msg.text) }} />
                    ) : (
                        <div>{msg.text}</div>
                    )}
                </div>
            ))}
        </div>
    );
}