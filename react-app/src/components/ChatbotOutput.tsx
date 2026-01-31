import { useSelector } from "react-redux";
import { RootState } from '../main';
import showdown from "showdown";

function convertMarkdownToHtml(markdownText: string) {
    if (!markdownText) return "";
    const text = String(markdownText);
    const converter = new showdown.Converter();
    return converter.makeHtml(text);
}

export default function ChatbotOutput() {
    const { messages } = useSelector((state: RootState) => state.chatbot);

    return (
        <div style={{ maxHeight: '600px', overflowY: 'auto', marginBottom: '20px', display: 'flex', flexDirection: 'column' }}>
            {messages.map((msg: any, index: number) => (
                <div key={index} style={{
                    alignSelf: msg.role === 'user' ? 'flex-end' : 'flex-start',
                    backgroundColor: msg.role === 'user' ? 'var(--primary)' : 'var(--tertiary)',
                    color: msg.role === 'user' ? 'var(--secondary)' : 'black',
                    padding: '10px',
                    borderRadius: '10px',
                    margin: '5px',
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