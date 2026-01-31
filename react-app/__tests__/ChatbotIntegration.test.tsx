import { render, screen } from '@testing-library/react';
import ChatbotOutput from '../src/components/ChatbotOutput';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import chatbotReducer from '../src/reducers/chatbotReducer';
import userReducer from '../src/reducers/userReducer';

const renderWithProviders = (
    ui: React.ReactElement,
    {
        preloadedState = {},
        store = configureStore({
            reducer: { chatbot: chatbotReducer, user: userReducer },
            preloadedState,
        }),
        ...renderOptions
    } = {}
) => {
    return {
        store, ...render(
            <Provider store={store}>{ui}</Provider>,
            renderOptions
        )
    }
}

describe('ChatbotOutput', () => {
    test('renders user and model messages correctly', () => {
        const initialState = {
            chatbot: {
                loading: false,
                messages: [
                    { role: 'user', text: 'Hello bot' },
                    { role: 'model', text: 'Hello human' }
                ],
                error: ''
            }
        };

        renderWithProviders(<ChatbotOutput />, { preloadedState: initialState });

        expect(screen.getByText('Hello bot')).toBeTruthy();
        // Model message is rendered via dangerouslySetInnerHTML, usually wrapped in divs by showdown
        // But since we mock it or just expect text content if showdown returns valid HTML containing text
        expect(screen.getByText(/Hello human/)).toBeTruthy();
    });
});
