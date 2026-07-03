// @vitest-environment jsdom
import { screen } from '@testing-library/react';
import { expect, describe, it } from 'vitest';
import '@testing-library/jest-dom';
import ChatbotOutput from '../src/components/ChatbotOutput';
import { renderWithProviders } from './testutils';

describe('ChatbotOutput', () => {
    it('renders user and model messages correctly', () => {
        const preloadedState = {
            chatbot: {
                loading: false,
                messages: [
                    { role: 'user', text: 'Hello bot' },
                    { role: 'model', text: 'Hello human' },
                ],
                error: '',
            },
        };

        renderWithProviders(<ChatbotOutput />, { preloadedState });

        expect(screen.getByText('Hello bot')).toBeInTheDocument();
        expect(screen.getByText(/Hello human/)).toBeInTheDocument();
    });

    it('renders model messages as markdown', () => {
        const preloadedState = {
            chatbot: {
                loading: false,
                messages: [
                    { role: 'model', text: 'Buy **index funds** and hold' },
                ],
                error: '',
            },
        };

        renderWithProviders(<ChatbotOutput />, { preloadedState });

        const bold = screen.getByText('index funds');
        expect(bold.tagName).toBe('STRONG');
        expect(screen.getByText(/and hold/)).toBeInTheDocument();
    });

    it('renders empty state when no messages exist', () => {
        const preloadedState = {
            chatbot: {
                loading: false,
                messages: [],
                error: '',
            },
        };

        const { container } = renderWithProviders(<ChatbotOutput />, { preloadedState });
        const outputDiv = container.firstChild as HTMLElement;
        expect(outputDiv.children).toHaveLength(0);
    });
});
