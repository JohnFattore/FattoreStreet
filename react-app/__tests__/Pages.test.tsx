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
import User from '../src/pages/User';
import Admin from '../src/pages/Admin';
import AdminSuccessBar from '../src/pages/AdminSuccessBar';
import Indexes from '../src/pages/Indexes';
import PriceComparison from '../src/components/PriceComparison';
import DividendComparison from '../src/components/DividendComparison';
import SplitComparison from '../src/components/SplitComparison';
import FilingSummaries from '../src/components/FilingSummaries';
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

    it('renders the filing summaries section', async () => {
        renderWithRoute(<SECData />, {
            path: '/sec-edgar/:ticker',
            initialEntry: '/sec-edgar/AAPL',
        });

        expect(await screen.findByText('10-K Filing Summaries')).toBeInTheDocument();
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

describe("User", () => {
    it("renders feedback ticket section for authenticated users", () => {
        renderWithProviders(<User />, { preloadedState: authenticatedState });
        expect(screen.getByText("Feedback Tickets")).toBeInTheDocument();
        expect(screen.getByText("Submit Product Feedback")).toBeInTheDocument();
    });
});

describe("Admin", () => {
    it("renders success bar jump button for spike user", () => {
        renderWithProviders(<Admin />, {
            preloadedState: {
                user: { access: "fake-token", refresh: "fake-refresh", username: "spike" },
            },
        });

        expect(screen.getByText("Open Corporate Action Success Bar")).toBeInTheDocument();
    });

    it("renders index admin actions and model hints for spike user", () => {
        renderWithProviders(<Admin />, {
            preloadedState: {
                user: { access: "fake-token", refresh: "fake-refresh", username: "spike" },
            },
        });

        expect(screen.getByRole("button", { name: /Refresh index metrics/i })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /Rebuild Fattore 50/i })).toBeInTheDocument();
        expect(screen.getByRole("heading", { name: /Refresh index stock metrics/i })).toBeInTheDocument();
        expect(screen.getByRole("heading", { name: /Rebuild Fattore 50 index/i })).toBeInTheDocument();
        expect(screen.getAllByText(/ListingIndexMetrics/i).length).toBeGreaterThanOrEqual(1);
    });
});

describe("Indexes", () => {
    it("renders the page heading", () => {
        renderWithProviders(<Indexes />);
        expect(screen.getByText("Indexes")).toBeInTheDocument();
    });
});

describe("AdminSuccessBar", () => {
    it("renders page title and empty-state table message", () => {
        renderWithProviders(<AdminSuccessBar />, {
            preloadedState: {
                user: { access: "fake-token", refresh: "fake-refresh", username: "spike" },
                adminSuccessBar: { tickers: [], error: "" },
            },
        });

        expect(screen.getByText("Corporate Action Success Bar")).toBeInTheDocument();
        expect(screen.getByText("No tickers added yet.")).toBeInTheDocument();
    });

    it("renders overview rows for persisted equity and ETF tickers", async () => {
        renderWithProviders(<AdminSuccessBar />, {
            preloadedState: {
                user: { access: "fake-token", refresh: "fake-refresh", username: "spike" },
                adminSuccessBar: { tickers: ["AAPL", "VOO"], error: "" },
            },
        });

        expect(await screen.findByText("AAPL")).toBeInTheDocument();
        expect(await screen.findByText("VOO")).toBeInTheDocument();
        expect(await screen.findByText("Combined Success")).toBeInTheDocument();
    });
});

describe('IexPricesView', () => {
    it('renders daily price table for a ticker', async () => {
        renderWithRoute(<IexPricesView />, {
            path: '/iex-prices/:ticker',
            initialEntry: '/iex-prices/AAPL',
        });

        expect(await screen.findByText('AAPL — IEX Daily Prices')).toBeInTheDocument();
        expect(await screen.findByText('3 trading days of adjusted OHLCV data from IEX exchange prices.')).toBeInTheDocument();
    });

    it('renders adjusted price comparison column headers', async () => {
        renderWithRoute(<IexPricesView />, {
            path: '/iex-prices/:ticker',
            initialEntry: '/iex-prices/AAPL',
        });

        expect(await screen.findByRole('columnheader', { name: /^Date$/ })).toBeInTheDocument();
        expect(await screen.findByRole('columnheader', { name: 'IEX Adjusted Close' })).toBeInTheDocument();
        expect(await screen.findByRole('columnheader', { name: 'YFinance Adjusted Close' })).toBeInTheDocument();
        expect((await screen.findAllByRole('columnheader', { name: 'Difference' })).length).toBeGreaterThan(0);
        expect(await screen.findByRole('columnheader', { name: '% Difference' })).toBeInTheDocument();
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

        expect(await screen.findByText('IEX vs YFinance Adjusted Price Comparison')).toBeInTheDocument();
    });

    it('renders the dividend comparison section', async () => {
        renderWithRoute(<IexPricesView />, {
            path: '/iex-prices/:ticker',
            initialEntry: '/iex-prices/AAPL',
        });

        expect(await screen.findByText('Internal vs YFinance Dividend Comparison')).toBeInTheDocument();
    });

    it('renders the split comparison section', async () => {
        renderWithRoute(<IexPricesView />, {
            path: '/iex-prices/:ticker',
            initialEntry: '/iex-prices/AAPL',
        });

        expect(await screen.findByText('Internal vs YFinance Split Comparison')).toBeInTheDocument();
    });
});

describe('PriceComparison', () => {
    it('renders column headers', async () => {
        renderWithProviders(<PriceComparison ticker="AAPL" />);

        expect(await screen.findByText('IEX Adjusted Close')).toBeInTheDocument();
        expect(screen.getByText('YFinance Adjusted Close')).toBeInTheDocument();
        expect(screen.getByText('Difference')).toBeInTheDocument();
        expect(screen.getByText('% Difference')).toBeInTheDocument();
    });

    it('renders overlapping date rows with diff values', async () => {
        renderWithProviders(<PriceComparison ticker="AAPL" />);

        expect(await screen.findByText('2025-03-15')).toBeInTheDocument();
        expect(await screen.findByText('2025-03-14')).toBeInTheDocument();
        expect(await screen.findByText('2025-03-13')).toBeInTheDocument();
    });

    it('renders the within-5-percent overlap summary', async () => {
        renderWithProviders(<PriceComparison ticker="AAPL" />);

        expect(await screen.findByText(/Within 3%:/)).toBeInTheDocument();
        expect(await screen.findByText(/3\/3 \(100\.00%\)/)).toBeInTheDocument();
    });
});

describe('FilingSummaries', () => {
    it('renders filing summary rows', async () => {
        renderWithProviders(<FilingSummaries ticker="AAPL" />);

        expect(await screen.findByText('10-K Filing Summaries')).toBeInTheDocument();
        expect(await screen.findByText('2024-11-01')).toBeInTheDocument();
        expect(await screen.findByText('2023-11-03')).toBeInTheDocument();
    });

    it('renders accession numbers', async () => {
        renderWithProviders(<FilingSummaries ticker="AAPL" />);

        expect(await screen.findByText('0000320193-24-000123')).toBeInTheDocument();
        expect(await screen.findByText('0000320193-23-000108')).toBeInTheDocument();
    });
});

describe('DividendComparison', () => {
    it('renders column headers', async () => {
        renderWithProviders(<DividendComparison ticker="AAPL" />);

        expect(await screen.findByText('Internal Date')).toBeInTheDocument();
        expect(screen.getByText('Internal Dividend')).toBeInTheDocument();
        expect(screen.getByText('YFinance Date')).toBeInTheDocument();
        expect(screen.getByText('YFinance Dividend')).toBeInTheDocument();
    });

    it('renders dividend rows', async () => {
        renderWithProviders(<DividendComparison ticker="AAPL" />);

        const febRows = await screen.findAllByText('2025-02-10');
        expect(febRows.length).toBeGreaterThan(0);
        const mayRows = await screen.findAllByText('2025-05-12');
        expect(mayRows.length).toBeGreaterThan(0);
    });
});

describe('SplitComparison', () => {
    it('renders column headers', async () => {
        renderWithProviders(<SplitComparison ticker="AAPL" />);

        expect(await screen.findByText('Internal Date')).toBeInTheDocument();
        expect(screen.getByText('Internal Ratio')).toBeInTheDocument();
        expect(screen.getByText('YFinance Date')).toBeInTheDocument();
        expect(screen.getByText('YFinance Ratio')).toBeInTheDocument();
    });

    it('renders split rows', async () => {
        renderWithProviders(<SplitComparison ticker="AAPL" />);

        const augRows = await screen.findAllByText('2020-08-31');
        expect(augRows.length).toBeGreaterThan(0);
    });
});
