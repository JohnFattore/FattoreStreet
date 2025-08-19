// @vitest-environment jsdom
import { render, screen, cleanup, waitFor, fireEvent, getByPlaceholderText } from '@testing-library/react';
import { act } from 'react';
import { expect, test, vi, afterEach } from 'vitest'
import { Provider } from "react-redux";
import '@testing-library/jest-dom';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import Portfolio from '../src/pages/Portfolio';
import Chatbot from '../src/pages/Chatbot';
import Restaurants from '../src/pages/Restaurants';
import { store } from '../src/store';
import WatchList from '../src/pages/WatchList';
import App from '../src/App.tsx';
import Visualizer from '../src/pages/Visualizer.tsx';
import EconomicIndicators from '../src/pages/EconomicIndicators.tsx';

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    localStorage.clear();
});

export function renderWrapped(ui) {
    return render(
        <Provider store={store}>
            <MemoryRouter>
                {ui}
            </MemoryRouter>
        </Provider>
    );
}

test('App Test', async () => {
    renderWrapped(<App />);
    expect(await screen.findByText('Welcome to Fattore Street!')).toBeInTheDocument();
});

test('Portfolio Test', async () => {
    renderWrapped(<Portfolio />);
    const usernameInput = screen.getByPlaceholderText('Username');
    const passwordInput = screen.getByPlaceholderText('Password');
    const submitButton = screen.getByRole('button', { name: /login/i });
  
    await userEvent.type(usernameInput, 'testUser');
    await userEvent.type(passwordInput, 'testPass');
  
    // Submit the form
    await act(async () => {
        await userEvent.click(submitButton);
    });
    expect(await screen.findByText('MSFT')).toBeInTheDocument();
});

test('Chatbot Test', async () => {
    renderWrapped(<Chatbot />);
    expect(await screen.findByText('Boglehead Chatbot')).toBeInTheDocument();
});

test('Watchlist Test', async () => {
    renderWrapped(<WatchList />);
    expect(await screen.findByText('Common Benchmarks')).toBeInTheDocument();
});

test('Visualizer Test', async () => {
    renderWrapped(<Visualizer />);
    expect(await screen.findByText('Assets')).toBeInTheDocument();
});

test('Economic Indicators Test', async () => {
    renderWrapped(<EconomicIndicators />);
    expect(await screen.findByText('CPI')).toBeInTheDocument();
});

test('Restaurants Test', async () => {
    renderWrapped(<Restaurants />);
    expect(await screen.findByText('Nashville Restaurants')).toBeInTheDocument();
});