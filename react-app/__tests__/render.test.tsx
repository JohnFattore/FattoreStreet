// @vitest-environment jsdom
import React from 'react';
import { screen } from '@testing-library/react';
import { act } from 'react';
import { expect, describe, it } from 'vitest';
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

const authenticatedState = {
    user: { access: 'fake-token', refresh: 'fake-refresh', username: 'testUser' }
};

describe('App', () => {
    it('renders the home page at root route', async () => {
        renderWithProviders(<App />);
        expect(await screen.findByText('Master Your Financial Future')).toBeInTheDocument();
    });
});

describe('Portfolio', () => {
    it('shows login form then loads accounts after login', async () => {
        renderWithProviders(<Portfolio />);
        const usernameInput = screen.getByPlaceholderText('Enter username');
        const passwordInput = screen.getByPlaceholderText('Enter password');
        const submitButton = screen.getByRole('button', { name: /login/i });

        await userEvent.type(usernameInput, 'testUser');
        await userEvent.type(passwordInput, 'testPass');

        await act(async () => {
            await userEvent.click(submitButton);
        });

        expect(await screen.findByText('Taxable Brokerage')).toBeInTheDocument();
        expect(await screen.findByText('Roth IRA')).toBeInTheDocument();
    });
});

describe('Chatbot', () => {
    it('renders chatbot heading', async () => {
        renderWithProviders(<Chatbot />, { preloadedState: authenticatedState });
        expect(await screen.findByText('Boglehead Chatbot')).toBeInTheDocument();
    });
});

describe('WatchList', () => {
    it('renders benchmark section heading', async () => {
        renderWithProviders(<WatchList />, { preloadedState: authenticatedState });
        expect(await screen.findByText('Common Benchmarks')).toBeInTheDocument();
    });
});

describe('Visualizer', () => {
    it('renders the comparison heading', async () => {
        renderWithProviders(<Visualizer />, { preloadedState: authenticatedState });
        expect(await screen.findByText('Assets vs S&P 500')).toBeInTheDocument();
    });
});

describe('EconomicIndicators', () => {
    it('renders page heading and section headings', async () => {
        renderWithProviders(<EconomicIndicators />, { preloadedState: authenticatedState });
        expect(await screen.findByText('Economic Indicators')).toBeInTheDocument();
        expect(await screen.findByText('Inflation')).toBeInTheDocument();
        expect(await screen.findByText('Labor Market')).toBeInTheDocument();
        expect(await screen.findAllByText(/Latest reading:/i)).not.toHaveLength(0);
    });
});

describe('Restaurants', () => {
    it('renders page heading', async () => {
        renderWithProviders(<Restaurants />, { preloadedState: authenticatedState });
        expect(await screen.findByText('Nashville Restaurants')).toBeInTheDocument();
    });
});
