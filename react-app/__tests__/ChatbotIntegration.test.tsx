// @vitest-environment jsdom
import { screen, waitFor } from "@testing-library/react";
import { expect, describe, it } from "vitest";
import "@testing-library/jest-dom";
import { http } from "msw";
import userEvent from "@testing-library/user-event";
import ChatbotOutput from "../src/components/ChatbotOutput";
import Chatbot from "../src/pages/Chatbot";
import { renderWithProviders } from "./testutils";
import { server } from "./mocks/server";

const djangoBaseUrl = (import.meta.env.VITE_APP_DJANGO_URL || "").endsWith("/")
  ? import.meta.env.VITE_APP_DJANGO_URL
  : `${import.meta.env.VITE_APP_DJANGO_URL}/`;
const conversationsUrl = djangoBaseUrl + "chatbot/api/conversations/";

const authenticatedState = {
  user: {
    access: "fake-token",
    refresh: "fake-refresh",
    username: "testuser",
    darkMode: false,
  },
};

const conversationDetail = (
  id: number,
  interactions: { input_text: string; output_text: string }[],
) => ({
  id,
  title: "Index funds",
  created_at: "2026-03-01T00:00:00Z",
  last_message_at: "2026-03-01T00:00:00Z",
  interactions: interactions.map((interaction) => ({
    ...interaction,
    timestamp: "2026-03-01T00:00:00Z",
  })),
});

describe("ChatbotOutput", () => {
  it("renders user and model messages of the selected conversation", async () => {
    server.use(
      http.get(conversationsUrl + ":id/", () =>
        Response.json(
          conversationDetail(1, [
            { input_text: "Hello bot", output_text: "Hello human" },
          ]),
        ),
      ),
    );

    renderWithProviders(<ChatbotOutput conversationId={1} />, {
      preloadedState: authenticatedState,
    });

    expect(await screen.findByText("Hello bot")).toBeInTheDocument();
    expect(screen.getByText(/Hello human/)).toBeInTheDocument();
  });

  it("renders model messages as markdown", async () => {
    server.use(
      http.get(conversationsUrl + ":id/", () =>
        Response.json(
          conversationDetail(1, [
            {
              input_text: "Any advice?",
              output_text: "Buy **index funds** and hold",
            },
          ]),
        ),
      ),
    );

    renderWithProviders(<ChatbotOutput conversationId={1} />, {
      preloadedState: authenticatedState,
    });

    const bold = await screen.findByText("index funds");
    expect(bold.tagName).toBe("STRONG");
    expect(screen.getByText(/and hold/)).toBeInTheDocument();
  });

  it("prompts for a first question in a new conversation", () => {
    renderWithProviders(<ChatbotOutput conversationId={null} />, {
      preloadedState: authenticatedState,
    });
    expect(
      screen.getByText("Ask a question to start a new conversation."),
    ).toBeInTheDocument();
  });

  it("renders nothing when logged out", () => {
    const { container } = renderWithProviders(
      <ChatbotOutput conversationId={null} />,
    );
    const outputDiv = container.firstChild as HTMLElement;
    expect(outputDiv.children).toHaveLength(0);
  });
});

describe("Chatbot conversations", () => {
  it("lists conversations and opens one", async () => {
    renderWithProviders(<Chatbot />, { preloadedState: authenticatedState });

    await userEvent.click(
      await screen.findByText("Should I buy index funds?", {
        selector: "span",
      }),
    );

    expect(
      await screen.findByText(/low-cost index funds are a great core holding/),
    ).toBeInTheDocument();
  });

  it("starts a new conversation, then sends follow-ups to it", async () => {
    const followUpUrls: string[] = [];
    server.use(
      http.post(conversationsUrl + ":id/messages/", ({ request }) => {
        followUpUrls.push(request.url);
        return Response.json({ message: "Stay the course." });
      }),
    );

    renderWithProviders(<Chatbot />, { preloadedState: authenticatedState });

    const input = screen.getByPlaceholderText("Start by typing here...");
    await userEvent.type(input, "Where do I start?");
    await userEvent.click(screen.getByRole("button", { name: /ask chatbot/i }));

    expect(await screen.findByText("Where do I start?")).toBeInTheDocument();
    expect(
      await screen.findByText(/Start with a total market fund/),
    ).toBeInTheDocument();

    // reset() empties the input directly, which user-event's own value tracking misses
    await userEvent.clear(input);
    await userEvent.type(input, "Should I panic sell?");
    await userEvent.click(screen.getByRole("button", { name: /ask chatbot/i }));

    // The follow-up shows optimistically, then the reply lands in the same conversation
    expect(await screen.findByText("Should I panic sell?")).toBeInTheDocument();
    expect(await screen.findByText(/Stay the course\./)).toBeInTheDocument();
    expect(followUpUrls).toEqual([conversationsUrl + "2/messages/"]);
  });

  it("renames a conversation", async () => {
    let patchedBody: unknown;
    server.use(
      http.patch(conversationsUrl + ":id/", async ({ request }) => {
        patchedBody = await request.json();
        return Response.json({});
      }),
    );

    renderWithProviders(<Chatbot />, { preloadedState: authenticatedState });

    await userEvent.click(
      await screen.findByRole("button", {
        name: "Rename Should I buy index funds?",
      }),
    );
    const titleInput = screen.getByLabelText("Conversation title");
    await userEvent.clear(titleInput);
    await userEvent.type(titleInput, "Getting started{Enter}");

    await waitFor(() =>
      expect(patchedBody).toEqual({ title: "Getting started" }),
    );
  });

  it("asks for confirmation before deleting a conversation", async () => {
    const deletedUrls: string[] = [];
    server.use(
      http.delete(conversationsUrl + ":id/", ({ request }) => {
        deletedUrls.push(request.url);
        return new Response(null, { status: 204 });
      }),
    );

    renderWithProviders(<Chatbot />, { preloadedState: authenticatedState });

    await userEvent.click(
      await screen.findByRole("button", {
        name: "Delete Should I buy index funds?",
      }),
    );
    expect(deletedUrls).toEqual([]);
    expect(screen.getByText("Delete this conversation?")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Delete" }));

    await waitFor(() => expect(deletedUrls).toEqual([conversationsUrl + "1/"]));
  });
});
