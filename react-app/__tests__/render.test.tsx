// @vitest-environment jsdom
import { screen, cleanup, waitFor, fireEvent } from '@testing-library/react';
import { act } from 'react';
import { expect, test, vi, afterEach } from 'vitest'
import '@testing-library/jest-dom';
import userEvent from '@testing-library/user-event';
import Portfolio from '../src/pages/Portfolio';
import Chatbot from '../src/pages/Chatbot';
import Restaurants from '../src/pages/Restaurants';
import WatchList from '../src/pages/WatchList';
import App from '../src/App.tsx';
import Visualizer from '../src/pages/Visualizer.tsx';
import EconomicIndicators from '../src/pages/EconomicIndicators.tsx';
import { renderWithProviders } from './testutils';

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    localStorage.clear();
});

const authenticatedState = {
    user: { access: 'fake-token', refresh: 'fake-refresh', username: 'testUser' }
};

test('App Test', async () => {
    renderWithProviders(<App />);
    expect(await screen.findByText('Welcome to Fattore Street!')).toBeInTheDocument();
});

test('Portfolio Test', async () => {
    renderWithProviders(<Portfolio />);
    const usernameInput = screen.getByPlaceholderText('Username');
    const passwordInput = screen.getByPlaceholderText('Password');
    const submitButton = screen.getByRole('button', { name: /login/i });

    await userEvent.type(usernameInput, 'testUser');
    await userEvent.type(passwordInput, 'testPass');

    // Submit the form
    await act(async () => {
        await userEvent.click(submitButton);
    });
    // Check for Account names from MSW
    expect(await screen.findByText('Taxable Brokerage')).toBeInTheDocument();
});

test('Chatbot Test', async () => {
    renderWithProviders(<Chatbot />, { preloadedState: authenticatedState });
    expect(await screen.findByText('Boglehead Chatbot')).toBeInTheDocument();
});

test('Watchlist Test', async () => {
    renderWithProviders(<WatchList />, { preloadedState: authenticatedState });
    expect(await screen.findByText('Common Benchmarks')).toBeInTheDocument();
});

test('Visualizer Test', async () => {
    renderWithProviders(<Visualizer />, { preloadedState: authenticatedState });
    expect(await screen.findByText('Assets vs S&P 500')).toBeInTheDocument();
});

test('Economic Indicators Test', async () => {
    renderWithProviders(<EconomicIndicators />, { preloadedState: authenticatedState });
    expect(await screen.findByText('CPI')).toBeInTheDocument();
});

test('Restaurants Test', async () => {
    renderWithProviders(<Restaurants />, { preloadedState: authenticatedState });
    expect(await screen.findByText('Nashville Restaurants')).toBeInTheDocument();
});