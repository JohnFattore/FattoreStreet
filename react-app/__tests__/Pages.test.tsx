// @vitest-environment jsdom
import React from 'react';
import { screen } from '@testing-library/react';
import { render } from '@testing-library/react';
import { expect, describe, it } from 'vitest';
import '@testing-library/jest-dom';
import { Provider } from 'react-redux';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import Home from '../src/pages/Home';
import SECData from '../src/pages/SECData';
import AccountView from '../src/pages/AccountView';
import Register from '../src/pages/Register';
import IexPricesView from '../src/pages/IexPricesView';
import PriceComparison from '../src/components/PriceComparison';
import { renderWithProviders, createTestStore } from './testutils';

const authenticatedState = {
    user: { access: 'fake-token', refresh: 'fake-refresh', username: 'testUser' }
};

function renderWithRoute(ui: React.ReactElement, { path, initialEntry, preloadedState = {} }: { path: string; initialEntry: string; preloadedState?: Record<string, unknown> }) {
    const store = createTestStore(preloadedState);
    return {
        ...render(
            <Provider store={store}>
                <MemoryRouter initialEntries={[initialEntry]}>
                    <Routes>
                        <Route path={path} element={ui} />
                    </Routes>
                </MemoryRouter>
            </Provider>
        ),
        store,
    };
}

describe('Home', () => {
    it('renders the hero section', () => {
        renderWithProviders(<Home />);
        expect(screen.getByText('Master Your Financial Future')).toBeInTheDocument();
    });

    it('renders platform capability cards', () => {
        renderWithProviders(<Home />);
        expect(screen.getByText('Portfolio Management')).toBeInTheDocument();
        expect(screen.getByText('Market Visualizer')).toBeInTheDocument();
        expect(screen.getByText('Live Watchlist')).toBeInTheDocument();
        expect(screen.getByText('Boglehead AI')).toBeInTheDocument();
        expect(screen.getByText('Macro Analytics')).toBeInTheDocument();
        expect(screen.getByText('Nashville Restaurants')).toBeInTheDocument();
    });

    it('renders the Buffett quote', () => {
        renderWithProviders(<Home />);
        expect(screen.getByText(/know-nothing investor/)).toBeInTheDocument();
        expect(screen.getByText(/Warren Buffett/)).toBeInTheDocument();
    });
});

describe('SECData', () => {
    it('renders company overview and financial data for a ticker', async () => {
        renderWithRoute(<SECData />, {
            path: '/sec-edgar/:ticker',
            initialEntry: '/sec-edgar/AAPL',
        });

        expect(await screen.findByText('SEC EDGAR Data: AAPL')).toBeInTheDocument();
        expect(await screen.findByText('Company Overview')).toBeInTheDocument();
        expect(await screen.findByText('0000320193')).toBeInTheDocument();
    });

    it('renders income statement and balance sheet sections', async () => {
        renderWithRoute(<SECData />, {
            path: '/sec-edgar/:ticker',
            initialEntry: '/sec-edgar/AAPL',
        });

        expect(await screen.findByText('TTM Income Statement')).toBeInTheDocument();
        expect(await screen.findByText('Latest Balance Sheet')).toBeInTheDocument();
        expect(await screen.findByText('Financial Ratios & Metrics')).toBeInTheDocument();
    });

    it('renders quarterly data table', async () => {
        renderWithRoute(<SECData />, {
            path: '/sec-edgar/:ticker',
            initialEntry: '/sec-edgar/AAPL',
        });

        expect(await screen.findByText('Historical Quarterly Data')).toBeInTheDocument();
    });
});

describe('AccountView', () => {
    it('renders account name and type after loading', async () => {
        renderWithRoute(<AccountView />, {
            path: '/account/:id',
            initialEntry: '/account/1',
            preloadedState: authenticatedState,
        });

        expect(await screen.findByText('Taxable Brokerage')).toBeInTheDocument();
        expect(await screen.findByText('TAXABLE ACCOUNT')).toBeInTheDocument();
    });
});

describe('Register', () => {
    it('renders the registration heading and form fields', () => {
        renderWithProviders(<Register />);
        expect(screen.getByText('Register for a Fattore Account')).toBeInTheDocument();
        expect(screen.getByPlaceholderText('Enter username')).toBeInTheDocument();
        expect(screen.getByPlaceholderText('Enter password')).toBeInTheDocument();
        expect(screen.getByPlaceholderText('Enter email')).toBeInTheDocument();
    });
});

describe('IexPricesView', () => {
    it('renders daily price table for a ticker', async () => {
        renderWithRoute(<IexPricesView />, {
            path: '/iex-prices/:ticker',
            initialEntry: '/iex-prices/AAPL',
        });

        expect(await screen.findByText('AAPL — IEX Daily Prices')).toBeInTheDocument();
        expect(await screen.findByText('3 trading days from IEX exchange data.')).toBeInTheDocument();
    });

    it('renders OHLCV column headers', async () => {
        renderWithRoute(<IexPricesView />, {
            path: '/iex-prices/:ticker',
            initialEntry: '/iex-prices/AAPL',
        });

        expect(await screen.findByText(/^Date/)).toBeInTheDocument();
        expect(await screen.findByText(/^Open/)).toBeInTheDocument();
        expect(await screen.findByText(/^High/)).toBeInTheDocument();
        expect(await screen.findByText(/^Low/)).toBeInTheDocument();
        expect(await screen.findByText(/^Close/)).toBeInTheDocument();
        expect(await screen.findByText(/^Volume/)).toBeInTheDocument();
    });

    it('renders a Back button', async () => {
        renderWithRoute(<IexPricesView />, {
            path: '/iex-prices/:ticker',
            initialEntry: '/iex-prices/AAPL',
        });

        expect(await screen.findByText('Back')).toBeInTheDocument();
    });

    it('renders the price comparison section', async () => {
        renderWithRoute(<IexPricesView />, {
            path: '/iex-prices/:ticker',
            initialEntry: '/iex-prices/AAPL',
        });

        expect(await screen.findByText('IEX vs YFinance Price Comparison')).toBeInTheDocument();
    });
});

describe('PriceComparison', () => {
    it('renders column headers', async () => {
        renderWithProviders(<PriceComparison ticker="AAPL" />);

        expect(await screen.findByText('IEX Close')).toBeInTheDocument();
        expect(screen.getByText('YFinance Close')).toBeInTheDocument();
        expect(screen.getByText('Difference')).toBeInTheDocument();
        expect(screen.getByText('% Difference')).toBeInTheDocument();
    });

    it('renders overlapping date rows with diff values', async () => {
        renderWithProviders(<PriceComparison ticker="AAPL" />);

        expect(await screen.findByText('2025-03-15')).toBeInTheDocument();
        expect(await screen.findByText('2025-03-14')).toBeInTheDocument();
        expect(await screen.findByText('2025-03-13')).toBeInTheDocument();
    });
});
