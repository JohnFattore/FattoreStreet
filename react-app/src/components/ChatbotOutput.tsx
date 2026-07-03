import { useSelector } from "react-redux";
import { RootState } from '../main';
import { IChatMessage } from '../interfaces';
import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkBreaks from "remark-breaks";

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
                        <Markdown remarkPlugins={[remarkGfm, remarkBreaks]}>{msg.text}</Markdown>
                    ) : (
                        <div>{msg.text}</div>
                    )}
                </div>
            ))}
        </div>
    );
}
