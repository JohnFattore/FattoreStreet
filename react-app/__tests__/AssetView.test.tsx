// @vitest-environment jsdom
import { screen } from '@testing-library/react';
import { expect, describe, it } from 'vitest';
import '@testing-library/jest-dom';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import AssetView from '../src/pages/AssetView';
import { renderWithProviders, renderWithRoute } from './testutils';
import { server } from './mocks/server';

const djangoBaseUrl = (import.meta.env.VITE_APP_DJANGO_URL || "").endsWith("/")
    ? import.meta.env.VITE_APP_DJANGO_URL
    : `${import.meta.env.VITE_APP_DJANGO_URL}/`;

const authenticatedState = {
    user: { access: 'fake-token', refresh: 'fake-refresh', username: 'testUser' },
};

const renderAssetView = (extraRoutes: { path: string; element: React.ReactElement }[] = []) =>
    renderWithRoute(<AssetView />, {
        path: '/asset/:ticker',
        initialEntry: '/asset/AAPL',
        preloadedState: authenticatedState,
        extraRoutes,
    });

describe('AssetView', () => {
    it('shows an error when no ticker is in the route', () => {
        renderWithProviders(<AssetView />);
        expect(screen.getByText('Error')).toBeInTheDocument();
    });

    it('shows the loading modal while asset info loads', () => {
        renderAssetView();
        expect(screen.getByText('Loading AAPL Data...')).toBeInTheDocument();
    });

    it('renders the ticker header, price chart card, and navigation buttons', async () => {
        renderAssetView();

        expect(await screen.findByText('Apple Inc.')).toBeInTheDocument();
        expect(screen.getByText(/EQUITY • AAPL/)).toBeInTheDocument();
        expect(screen.getByText('Price history')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'SEC EDGAR Data' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'IEX Price History' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Back to Portfolio' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Back to WatchList' })).toBeInTheDocument();
    });

    it('shows the server error when asset info fails', async () => {
        server.use(
            http.get(djangoBaseUrl.concat('portfolio/api/asset-info/'), () =>
                Response.json({ detail: 'yfinance is down' }, { status: 500 })
            )
        );

        renderAssetView();

        expect(await screen.findByText('AAPL View')).toBeInTheDocument();
        expect(screen.getByText('yfinance is down')).toBeInTheDocument();
    });

    it('navigates to SEC EDGAR data for the ticker', async () => {
        renderAssetView([{ path: '/sec-edgar/:ticker', element: <div>sec-edgar-probe</div> }]);

        await userEvent.click(await screen.findByRole('button', { name: 'SEC EDGAR Data' }));
        expect(await screen.findByText('sec-edgar-probe')).toBeInTheDocument();
    });

    it('navigates back to the portfolio', async () => {
        renderAssetView([{ path: '/portfolio', element: <div>portfolio-probe</div> }]);

        await userEvent.click(await screen.findByRole('button', { name: 'Back to Portfolio' }));
        expect(await screen.findByText('portfolio-probe')).toBeInTheDocument();
    });
});
