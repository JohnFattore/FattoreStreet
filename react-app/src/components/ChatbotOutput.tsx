import { useSelector } from "react-redux";
import { RootState } from "../main";
import { IChatMessage } from "../interfaces";
import {
  NEW_CONVERSATION_CACHE_KEY,
  useCreateConversationMutation,
  useGetConversationQuery,
} from "../functions/api";
import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkBreaks from "remark-breaks";

export default function ChatbotOutput({
  conversationId,
}: {
  conversationId: number | null;
}) {
  const { access } = useSelector((state: RootState) => state.user);
  const { data: conversation } = useGetConversationQuery(conversationId ?? 0, {
    skip: !access || conversationId === null,
  });
  // A new conversation has no cache entry until its first answer arrives, so the
  // pending question is read from the shared create mutation instead
  const [, { isLoading: isCreating, originalArgs: pendingQuestion }] =
    useCreateConversationMutation({
      fixedCacheKey: NEW_CONVERSATION_CACHE_KEY,
    });

  let messages: IChatMessage[] = [];
  if (conversationId !== null) {
    messages = conversation?.messages ?? [];
  } else if (isCreating && pendingQuestion) {
    messages = [{ role: "user", text: pendingQuestion }];
  }

  return (
    <div
      className="overflow-auto mb-4 d-flex flex-column"
      style={{ maxHeight: "600px" }}
    >
      {messages.length === 0 && access ? (
        <p className="text-muted">
          Ask a question to start a new conversation.
        </p>
      ) : null}
      {messages.map((msg: IChatMessage, index: number) => (
        <div
          key={index}
          className={`p-2 rounded-3 m-1 ${msg.role === "user" ? "align-self-end" : "align-self-start"}`}
          style={{
            backgroundColor:
              msg.role === "user" ? "var(--primary)" : "var(--tertiary)",
            color: msg.role === "user" ? "var(--secondary)" : "var(--primary)",
            maxWidth: "80%",
          }}
        >
          {msg.role === "model" ? (
            <Markdown remarkPlugins={[remarkGfm, remarkBreaks]}>
              {msg.text}
            </Markdown>
          ) : (
            <div>{msg.text}</div>
          )}
        </div>
      ))}
    </div>
  );
}
