// @vitest-environment jsdom
import { screen, waitFor } from '@testing-library/react';
import { expect, describe, it } from 'vitest';
import '@testing-library/jest-dom';
import { http } from 'msw';
import userEvent from '@testing-library/user-event';
import ChatbotOutput from '../src/components/ChatbotOutput';
import ChatbotForm from '../src/components/ChatbotForm';
import { renderWithProviders } from './testutils';
import { server } from './mocks/server';

const djangoBaseUrl = (import.meta.env.VITE_APP_DJANGO_URL || "").endsWith("/")
    ? import.meta.env.VITE_APP_DJANGO_URL
    : `${import.meta.env.VITE_APP_DJANGO_URL}/`;
const chatbotUrl = djangoBaseUrl + "chatbot/api/chatbot/";

const authenticatedState = {
    user: { access: "fake-token", refresh: "fake-refresh", username: "testuser", darkMode: false },
};

describe('ChatbotOutput', () => {
    it('renders user and model messages correctly', async () => {
        server.use(
            http.get(chatbotUrl, () =>
                Response.json(
                    [{ input_text: 'Hello bot', output_text: 'Hello human', timestamp: '2026-03-01T00:00:00Z' }],
                    { status: 200 }
                )
            )
        );

        renderWithProviders(<ChatbotOutput />, { preloadedState: authenticatedState });

        expect(await screen.findByText('Hello bot')).toBeInTheDocument();
        expect(screen.getByText(/Hello human/)).toBeInTheDocument();
    });

    it('renders model messages as markdown', async () => {
        server.use(
            http.get(chatbotUrl, () =>
                Response.json(
                    [{ input_text: 'Any advice?', output_text: 'Buy **index funds** and hold', timestamp: '2026-03-01T00:00:00Z' }],
                    { status: 200 }
                )
            )
        );

        renderWithProviders(<ChatbotOutput />, { preloadedState: authenticatedState });

        const bold = await screen.findByText('index funds');
        expect(bold.tagName).toBe('STRONG');
        expect(screen.getByText(/and hold/)).toBeInTheDocument();
    });

    it('renders empty state when no messages exist', async () => {
        server.use(
            http.get(chatbotUrl, () => Response.json([], { status: 200 }))
        );

        const { container } = renderWithProviders(<ChatbotOutput />, { preloadedState: authenticatedState });
        const outputDiv = container.firstChild as HTMLElement;
        await waitFor(() => expect(outputDiv.children).toHaveLength(0));
    });

    it('does not fetch history when logged out', () => {
        const { container } = renderWithProviders(<ChatbotOutput />);
        const outputDiv = container.firstChild as HTMLElement;
        expect(outputDiv.children).toHaveLength(0);
    });
});

describe('Chatbot send flow', () => {
    it('optimistically shows the user message, then appends the model reply', async () => {
        server.use(
            http.get(chatbotUrl, () =>
                Response.json(
                    [{ input_text: 'Earlier question', output_text: 'Earlier answer', timestamp: '2026-03-01T00:00:00Z' }],
                    { status: 200 }
                )
            )
        );

        renderWithProviders(
            <>
                <ChatbotOutput />
                <ChatbotForm />
            </>,
            { preloadedState: authenticatedState }
        );

        // Wait for the history query to settle so the optimistic update has a cache to patch
        expect(await screen.findByText('Earlier question')).toBeInTheDocument();

        await userEvent.type(
            screen.getByPlaceholderText('Start by typing here...'),
            'Should I panic sell?'
        );
        await userEvent.click(screen.getByRole('button', { name: /ask chatbot/i }));

        // The user's message lands in the getChatbot cache before the reply arrives
        expect(await screen.findByText('Should I panic sell?')).toBeInTheDocument();
        // Default POST handler replies with this message
        expect(await screen.findByText(/Stay the course and keep costs low/)).toBeInTheDocument();
    });
});
