// @vitest-environment jsdom
import { screen } from '@testing-library/react';
import { expect, describe, it } from 'vitest';
import '@testing-library/jest-dom';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import Admin from '../src/pages/Admin';
import { renderWithProviders, renderWithRoute } from './testutils';
import { server } from './mocks/server';

const springbootUrl: string = import.meta.env.VITE_APP_SPRINGBOOT_URL;

const spikeState = {
    user: { access: 'fake-token', refresh: 'fake-refresh', username: 'spike' },
};

// Form.Check switches carry no ids, so labels are not programmatically
// associated — select by document order instead:
// 0 = overwrite ETF identities, 1 = force re-fetch, 2 = ETF only,
// 3 = equity only, 4 = rebuild refresh-metrics-first
const getSwitches = () => screen.getAllByRole('checkbox');

describe('Admin auth gates', () => {
    it('asks for sign in without an access token', () => {
        renderWithProviders(<Admin />);
        expect(screen.getByText('Sign in to React Admin')).toBeInTheDocument();
    });

    it('rejects non-spike users', () => {
        renderWithProviders(<Admin />, {
            preloadedState: { user: { access: 'fake-token', refresh: 'r', username: 'testUser' } },
        });
        expect(screen.getByRole('heading', { name: 'Error' })).toBeInTheDocument();
    });

    it('welcomes spike', () => {
        renderWithProviders(<Admin />, { preloadedState: spikeState });
        expect(screen.getByText('Welcome Spike')).toBeInTheDocument();
    });
});

describe('Asset Load', () => {
    it('shows the job result on success', async () => {
        renderWithProviders(<Admin />, { preloadedState: spikeState });

        await userEvent.click(screen.getByRole('button', { name: 'Asset Load' }));
        expect(await screen.findByText('asset-load ok')).toBeInTheDocument();
    });

    it('sends overwriteExisting=true when the switch is on', async () => {
        renderWithProviders(<Admin />, { preloadedState: spikeState });

        await userEvent.click(getSwitches()[0]);
        await userEvent.click(screen.getByRole('button', { name: 'Asset Load' }));
        expect(await screen.findByText('asset-load ok?overwriteExisting=true')).toBeInTheDocument();
    });

    it('falls back to the legacy load endpoint on 404', async () => {
        server.use(
            http.get(springbootUrl.concat('admin/asset-load'), () =>
                new Response('not here', { status: 404 })
            )
        );
        renderWithProviders(<Admin />, { preloadedState: spikeState });

        await userEvent.click(screen.getByRole('button', { name: 'Asset Load' }));
        expect(await screen.findByText('legacy load ok')).toBeInTheDocument();
    });

    it('surfaces the fallback error when both endpoints fail', async () => {
        server.use(
            http.get(springbootUrl.concat('admin/asset-load'), () =>
                new Response('not here', { status: 404 })
            ),
            http.get(springbootUrl.concat('admin/load'), () =>
                new Response('legacy loader exploded', { status: 500 })
            )
        );
        renderWithProviders(<Admin />, { preloadedState: spikeState });

        await userEvent.click(screen.getByRole('button', { name: 'Asset Load' }));
        expect(await screen.findByText('legacy loader exploded')).toBeInTheDocument();
    });
});

describe('Sync Frames', () => {
    it('shows the job result on success', async () => {
        renderWithProviders(<Admin />, { preloadedState: spikeState });

        await userEvent.click(screen.getByRole('button', { name: 'Full Sync' }));
        expect(await screen.findByText('sync-frames ok')).toBeInTheDocument();
    });

    it('explains 401 responses with the shared-secret hint', async () => {
        server.use(
            http.get(springbootUrl.concat('admin/sync-frames'), () =>
                new Response('unauthorized', { status: 401 })
            )
        );
        renderWithProviders(<Admin />, { preloadedState: spikeState });

        await userEvent.click(screen.getByRole('button', { name: 'Full Sync' }));
        expect(await screen.findByText(/SEC API returned 401/)).toBeInTheDocument();
    });
});

describe('Download IEX HIST', () => {
    it('sends the entered number of trading days', async () => {
        renderWithProviders(<Admin />, { preloadedState: spikeState });

        const daysInput = screen.getByDisplayValue('252');
        await userEvent.clear(daysInput);
        await userEvent.type(daysInput, '30');
        await userEvent.click(screen.getByRole('button', { name: 'Download HIST' }));
        expect(await screen.findByText('load-hist ok?days=30')).toBeInTheDocument();
    });
});

describe('Adjust Prices', () => {
    it('sends ticker, force, etfOnly, and minConfidence', async () => {
        renderWithProviders(<Admin />, { preloadedState: spikeState });

        await userEvent.type(screen.getAllByPlaceholderText('e.g. AAPL')[0], 'aapl');
        await userEvent.click(getSwitches()[1]); // force re-fetch
        await userEvent.click(getSwitches()[2]); // ETF only
        await userEvent.click(screen.getByRole('button', { name: 'Adjust Prices' }));

        expect(
            await screen.findByText('adjust-prices ok?ticker=AAPL&force=true&etfOnly=true&minConfidence=70')
        ).toBeInTheDocument();
    });

    it('ETF only and Equity only switches are mutually exclusive', async () => {
        renderWithProviders(<Admin />, { preloadedState: spikeState });

        await userEvent.click(getSwitches()[2]); // ETF only on
        expect(getSwitches()[2]).toBeChecked();

        await userEvent.click(getSwitches()[3]); // Equity only on → ETF off
        expect(getSwitches()[3]).toBeChecked();
        expect(getSwitches()[2]).not.toBeChecked();

        await userEvent.click(getSwitches()[2]); // ETF back on → Equity off
        expect(getSwitches()[2]).toBeChecked();
        expect(getSwitches()[3]).not.toBeChecked();
    });
});

describe('Summarize Filings', () => {
    it('sends the uppercased ticker', async () => {
        renderWithProviders(<Admin />, { preloadedState: spikeState });

        await userEvent.type(screen.getAllByPlaceholderText('e.g. AAPL')[1], 'msft');
        await userEvent.click(screen.getByRole('button', { name: 'Summarize Filings' }));
        expect(await screen.findByText('summarize-filings ok?ticker=MSFT')).toBeInTheDocument();
    });
});

describe('Refresh index metrics', () => {
    it('sends no params for the default russell1000 scope', async () => {
        renderWithProviders(<Admin />, { preloadedState: spikeState });

        await userEvent.click(screen.getByRole('button', { name: 'Refresh index metrics' }));
        expect(await screen.findByText('refresh-stocks ok')).toBeInTheDocument();
    });

    it('sends scope=all when selected', async () => {
        renderWithProviders(<Admin />, { preloadedState: spikeState });

        const scopeSelect = screen.getAllByRole('combobox')[0];
        await userEvent.selectOptions(scopeSelect, 'all');
        await userEvent.click(screen.getByRole('button', { name: 'Refresh index metrics' }));
        expect(await screen.findByText('refresh-stocks ok?scope=all')).toBeInTheDocument();
    });

    it('a ticker wins over scope and disables the scope select', async () => {
        renderWithProviders(<Admin />, { preloadedState: spikeState });

        await userEvent.type(screen.getByPlaceholderText('e.g. NFLX'), 'nflx');
        expect(screen.getAllByRole('combobox')[0]).toBeDisabled();

        await userEvent.click(screen.getByRole('button', { name: 'Refresh index metrics' }));
        expect(await screen.findByText('refresh-stocks ok?ticker=NFLX')).toBeInTheDocument();
    });
});

describe('Rebuild indexes', () => {
    it('sends no params by default (all indexes)', async () => {
        renderWithProviders(<Admin />, { preloadedState: spikeState });

        await userEvent.click(screen.getByRole('button', { name: 'Rebuild index(es)' }));
        expect(await screen.findByText('rebuild ok')).toBeInTheDocument();
    });

    it('sends refreshMetrics and the selected code', async () => {
        renderWithProviders(<Admin />, { preloadedState: spikeState });

        await userEvent.selectOptions(screen.getAllByRole('combobox')[1], 'FAT100');
        await userEvent.click(getSwitches()[4]); // refresh metrics first
        await userEvent.click(screen.getByRole('button', { name: 'Rebuild index(es)' }));
        expect(await screen.findByText('rebuild ok?refreshMetrics=true&code=FAT100')).toBeInTheDocument();
    });
});

describe('Admin navigation', () => {
    it('navigates to the success bar page', async () => {
        renderWithRoute(<Admin />, {
            path: '/react-admin',
            initialEntry: '/react-admin',
            preloadedState: spikeState,
            extraRoutes: [
                { path: '/react-admin/success-bar', element: <div>success-bar-probe</div> },
            ],
        });

        await userEvent.click(screen.getByText('Open Corporate Action Success Bar'));
        expect(await screen.findByText('success-bar-probe')).toBeInTheDocument();
    });
});
