// @vitest-environment jsdom
import { screen } from '@testing-library/react';
import { expect, describe, it } from 'vitest';
import '@testing-library/jest-dom';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import WatchListTable from '../src/components/WatchListTable';
import YFinanceQuartersTable from '../src/components/YFinanceQuartersTable';
import { IEquityInfo } from '../src/interfaces';
import { renderWithProviders, renderWithRoute } from './testutils';
import { server } from './mocks/server';

const djangoBaseUrl = (import.meta.env.VITE_APP_DJANGO_URL || "").endsWith("/")
    ? import.meta.env.VITE_APP_DJANGO_URL
    : `${import.meta.env.VITE_APP_DJANGO_URL}/`;

const msftInfo: IEquityInfo = {
    ticker: 'MSFT',
    shortName: 'Microsoft Corporation',
    longName: 'Microsoft Corporation',
    type: 'EQUITY',
    exchange: 'NASDAQ',
    market: 'us_market',
    currentPrice: 500,
    percentChangeDaily: 0.02,
    percentChangeWeekly: 0.03,
    percentChangeMonthly: 0.04,
    percentChangeYTD: 0.05,
    percentChangeYearly: 0.06,
    percentChange3Years: 0.2,
    percentChange5Years: 0.5,
    dividendYield: 0.008,
    marketCap: 1000000000,
    trailingPE: 10,
    incomeTTM: 100000000,
    revenueTTM: 500000000,
    netMarginTTM: 0.2,
};

const tableProps = {
    tickers: ['MSFT'],
    assetInfos: { MSFT: msftInfo },
    assetInfosLoading: false,
    assetInfosError: undefined,
};

describe('WatchListTable performance view', () => {
    it('formats price and percent-change cells', () => {
        renderWithProviders(<WatchListTable {...tableProps} />);

        expect(screen.getByText('Microsoft Corporation')).toBeInTheDocument();
        expect(screen.getByText('$500.00')).toBeInTheDocument();
        expect(screen.getByText('2.00%')).toBeInTheDocument();
        expect(screen.getByText('5.00%')).toBeInTheDocument();
    });

    it('Remove updates the watchList slice and localStorage', async () => {
        const { store } = renderWithProviders(<WatchListTable {...tableProps} />, {
            preloadedState: {
                watchList: { tickers: ['MSFT'], loading: false, error: '', ticker: '' },
            },
        });

        await userEvent.click(screen.getByRole('button', { name: 'Remove' }));

        expect(store.getState().watchList.tickers).toEqual([]);
        expect(JSON.parse(localStorage.getItem('tickers') as string)).toEqual([]);
    });

    it('View navigates to the asset page', async () => {
        renderWithRoute(<WatchListTable {...tableProps} />, {
            path: '/',
            initialEntry: '/',
            extraRoutes: [{ path: '/asset/:ticker', element: <div>asset-probe</div> }],
        });

        await userEvent.click(screen.getByRole('button', { name: 'View' }));
        expect(await screen.findByText('asset-probe')).toBeInTheDocument();
    });

    it('passes loading and error through to the table state', () => {
        const { container, unmount } = renderWithProviders(
            <WatchListTable {...tableProps} assetInfos={undefined} assetInfosLoading={true} />
        );
        expect(container.querySelector('.spinner-border')).toBeInTheDocument();
        unmount();

        renderWithProviders(
            <WatchListTable
                {...tableProps}
                assetInfos={undefined}
                assetInfosError={{ data: { detail: 'yfinance exploded' } }}
            />
        );
        expect(screen.getByText('yfinance exploded')).toBeInTheDocument();
    });
});

describe('WatchListTable SEC Edgar view', () => {
    it('loads batch fact sheets when toggled', async () => {
        renderWithProviders(<WatchListTable {...tableProps} tickers={['AAPL']} />);

        await userEvent.click(screen.getByRole('button', { name: 'SEC Edgar' }));

        expect(await screen.findByText('$383.29 Billion')).toBeInTheDocument();
        expect(screen.getByText('24.46%')).toBeInTheDocument();
        expect(screen.getByText('2024-12-28')).toBeInTheDocument();
    });

    it('filters out tickers whose fact-sheet request fails', async () => {
        server.use(
            http.get(
                import.meta.env.VITE_APP_SPRINGBOOT_URL.concat('company-fact-sheet'),
                ({ request }) => {
                    const ticker = new URL(request.url).searchParams.get('ticker');
                    if (ticker === 'BAD') {
                        return new Response('unknown ticker', { status: 404 });
                    }
                    return Response.json(
                        { ticker, cik: '0000320193', ttmRevenue: '383285000000', netMargin: '24.46%', latestQuarterEnd: '2024-12-28' },
                        { status: 200 }
                    );
                }
            )
        );

        renderWithProviders(<WatchListTable {...tableProps} tickers={['AAPL', 'BAD']} />);
        await userEvent.click(screen.getByRole('button', { name: 'SEC Edgar' }));

        expect(await screen.findByText('AAPL')).toBeInTheDocument();
        // header row + exactly one data row — the failed ticker is filtered, not crashed on
        expect(screen.getAllByRole('row')).toHaveLength(2);
        expect(screen.queryByText('BAD')).not.toBeInTheDocument();
    });
});

describe('YFinanceQuartersTable', () => {
    it('renders formatted quarter rows', async () => {
        renderWithProviders(<YFinanceQuartersTable ticker="AAPL" />);

        expect(await screen.findByText('2024 Q4')).toBeInTheDocument();
        expect(screen.getByText('$124.30 Billion')).toBeInTheDocument();
        expect(screen.getByText('$2.41')).toBeInTheDocument();
    });

    it('renders dashes for null EPS fields', async () => {
        server.use(
            http.get(djangoBaseUrl.concat('portfolio/api/quarterly-data/'), () =>
                Response.json(
                    [
                        {
                            year: 2023,
                            quarter: 1,
                            periodEnd: '2023-04-01',
                            revenues: 94836000000,
                            netIncomeLoss: null,
                            earningsPerShareBasic: null,
                            earningsPerShareDiluted: null,
                        },
                    ],
                    { status: 200 }
                )
            )
        );

        renderWithProviders(<YFinanceQuartersTable ticker="AAPL" />);

        expect(await screen.findByText('2023 Q1')).toBeInTheDocument();
        expect(screen.getByText('$94.84 Billion')).toBeInTheDocument();
        expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(2);
    });

    it('shows the API error in the table state', async () => {
        server.use(
            http.get(djangoBaseUrl.concat('portfolio/api/quarterly-data/'), () =>
                Response.json({ detail: 'quarters unavailable' }, { status: 500 })
            )
        );

        renderWithProviders(<YFinanceQuartersTable ticker="AAPL" />);

        expect(await screen.findByText('quarters unavailable')).toBeInTheDocument();
    });
});
