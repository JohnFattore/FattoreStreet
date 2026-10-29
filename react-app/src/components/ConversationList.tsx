import { useRef, useState } from "react";
import { Button, ButtonGroup, Form, ListGroup } from "react-bootstrap";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import {
  useDeleteConversationMutation,
  useGetConversationsQuery,
  useRenameConversationMutation,
} from "../functions/api";
import { IConversation } from "../interfaces";

export default function ConversationList({
  selectedId,
  onSelect,
}: {
  selectedId: number | null;
  onSelect: (id: number | null) => void;
}) {
  const { access } = useSelector((state: RootState) => state.user);
  const { data: conversations = [] } = useGetConversationsQuery(undefined, {
    skip: !access,
  });
  const [renameConversation] = useRenameConversationMutation();
  const [deleteConversation] = useDeleteConversationMutation();
  const [editingId, setEditingId] = useState<number | null>(null);
  // Enter and Escape both end editing, and the input blurs as it unmounts; the ref
  // makes sure only the first of those decides whether the rename is saved
  const editingRef = useRef<number | null>(null);
  const [draftTitle, setDraftTitle] = useState("");
  const [confirmingDeleteId, setConfirmingDeleteId] = useState<number | null>(
    null,
  );

  const startRename = (conversation: IConversation) => {
    editingRef.current = conversation.id;
    setEditingId(conversation.id);
    setDraftTitle(conversation.title);
  };

  const stopEditing = () => {
    editingRef.current = null;
    setEditingId(null);
  };

  const saveRename = (conversation: IConversation) => {
    if (editingRef.current !== conversation.id) return;
    const title = draftTitle.trim();
    if (title && title !== conversation.title) {
      renameConversation({ id: conversation.id, title });
    }
    stopEditing();
  };

  const confirmDelete = (id: number) => {
    deleteConversation(id);
    setConfirmingDeleteId(null);
    if (id === selectedId) onSelect(null);
  };

  const renderItem = (conversation: IConversation) => {
    if (editingId === conversation.id) {
      return (
        <Form.Control
          size="sm"
          aria-label="Conversation title"
          value={draftTitle}
          maxLength={100}
          autoFocus
          onChange={(e) => setDraftTitle(e.target.value)}
          onBlur={() => saveRename(conversation)}
          onKeyDown={(e) => {
            if (e.key === "Enter") saveRename(conversation);
            if (e.key === "Escape") stopEditing();
          }}
        />
      );
    }
    if (confirmingDeleteId === conversation.id) {
      return (
        <div className="d-flex align-items-center justify-content-between">
          <span>Delete this conversation?</span>
          <ButtonGroup size="sm">
            <Button
              variant="danger"
              onClick={() => confirmDelete(conversation.id)}
            >
              Delete
            </Button>
            <Button
              variant="secondary"
              onClick={() => setConfirmingDeleteId(null)}
            >
              Cancel
            </Button>
          </ButtonGroup>
        </div>
      );
    }
    return (
      <div className="d-flex align-items-center justify-content-between">
        <span
          role="button"
          className="text-truncate flex-grow-1"
          onClick={() => onSelect(conversation.id)}
        >
          {conversation.title}
        </span>
        <ButtonGroup size="sm">
          <Button
            variant="link"
            aria-label={`Rename ${conversation.title}`}
            onClick={() => startRename(conversation)}
          >
            Rename
          </Button>
          <Button
            variant="link"
            className="text-danger"
            aria-label={`Delete ${conversation.title}`}
            onClick={() => setConfirmingDeleteId(conversation.id)}
          >
            Delete
          </Button>
        </ButtonGroup>
      </div>
    );
  };

  return (
    <div className="mb-4">
      <Button className="w-100 mb-2" onClick={() => onSelect(null)}>
        New chat
      </Button>
      <ListGroup>
        {conversations.map((conversation) => (
          <ListGroup.Item
            key={conversation.id}
            active={conversation.id === selectedId}
          >
            {renderItem(conversation)}
          </ListGroup.Item>
        ))}
      </ListGroup>
    </div>
  );
}
