/// <reference types="vite/client" />
// @vitest-environment jsdom
import { screen, cleanup } from '@testing-library/react';
import { expect, test, vi, afterEach } from 'vitest'
import '@testing-library/jest-dom';
import { http, delay } from 'msw';
import AccountList from '../src/components/AccountList';
import AssetTable from '../src/components/AssetTable';
import AssetTickerTable from '../src/components/AssetTickerTable';
import { renderWithProviders } from './testutils';
import { server } from './mocks/server';

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

const authenticatedState = {
    user: { access: 'fake-token', refresh: 'fake-refresh', username: 'testuser' }
};

test('AccountList renders and calculates balances correctly', async () => {
    renderWithProviders(<AccountList />, { preloadedState: authenticatedState });

    // Check for account names
    expect(await screen.findByText('Taxable Brokerage')).toBeInTheDocument();
    expect(await screen.findByText('Roth IRA')).toBeInTheDocument();

    // Check for calculated balances and daily changes
    expect(await screen.findByText('$5,000.00')).toBeInTheDocument();
    expect(await screen.findByText('$4,000.00')).toBeInTheDocument();
    expect(await screen.findByText(/\$98\.04 \(2\.00%\)/)).toBeInTheDocument();
    expect(await screen.findByText(/-\$40\.40 \(-1\.00%\)/)).toBeInTheDocument();
});

test('AssetTable shows Loading... when asset info is loading', async () => {
    server.use(
        http.get(
            `${import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL}asset-info/`,
            async () => {
                await delay(500);
                return Response.json(
                    {
                        data: [
                            {
                                ticker: "MSFT",
                                short_name: "Microsoft Corporation",
                                long_name: "Microsoft Corporation",
                                type: "EQUITY",
                                current_price: 500,
                                percent_change_daily: 0.02,
                            },
                        ]
                    },
                    { status: 200 }
                );
            }
        )
    );

    renderWithProviders(<AssetTable />, { preloadedState: authenticatedState });

    expect(await screen.findByText('Assets Owned')).toBeInTheDocument();
    expect(await screen.findByText('MSFT')).toBeInTheDocument();

    const loadingElements = await screen.findAllByText('Loading...');
    expect(loadingElements.length).toBeGreaterThan(0);

    expect(await screen.findByText('Microsoft Corporation')).toBeInTheDocument();
    expect(screen.queryByText('Loading...')).not.toBeInTheDocument();
});

test('AssetTickerTable shows Loading... when asset info is loading', async () => {
    server.use(
        http.get(
            `${import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL}asset-info/`,
            async () => {
                await delay(500);
                return Response.json(
                    {
                        data: [
                            {
                                ticker: "AAPL",
                                short_name: "Apple Inc.",
                                long_name: "Apple Inc.",
                                type: "EQUITY",
                                current_price: 150,
                                percent_change_daily: -0.01,
                            },
                        ]
                    },
                    { status: 200 }
                );
            }
        )
    );

    renderWithProviders(<AssetTickerTable ticker="AAPL" />, { preloadedState: authenticatedState });

    expect(await screen.findByText('Purchased Assets')).toBeInTheDocument();

    const loadingElements = await screen.findAllByText('Loading...');
    expect(loadingElements.length).toBeGreaterThan(0);

    expect(await screen.findByText('$3,000.00')).toBeInTheDocument(); // 20 shares * 150
    expect(screen.queryByText('Loading...')).not.toBeInTheDocument();
});
