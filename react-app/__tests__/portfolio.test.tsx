// @vitest-environment jsdom
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import { expect, test, vi, afterEach } from 'vitest'
import React from 'react';
import Portfolio from '../src/pages/Portfolio'
import AssetForm from '../src/components/AssetForm'
import AssetTable from '../src/components/AssetTable'
import AssetRow from '../src/components/AssetRow'
import { IAsset } from '../src/interfaces';

afterEach(() => { cleanup(); });

vi.mock('../src/components/AxiosFunctions', () => ({
    __esModule: true,
    getAssets: vi.fn(() => new Promise((resolve) => resolve({ data: [{ ticker: "VTI", shares: 5, costbasis: 180, buy: '2023-02-14' }] }))),
    getQuote: vi.fn(() => new Promise((resolve) => resolve({ data: [{ c: "200", dp: 1.5 }] }))),
    postAsset: vi.fn(() => new Promise((resolve) => resolve({ data: [{ c: "200", dp: 1.5 }] }))),
}));

test('Portfolio Page Test', () => {
    render(<Portfolio />);
    expect(screen.queryByRole('assetTableHeader')?.textContent).to.include("User's Portfolio");
});

//console.log is just a random function passed for the sake of testing
test('Asset Table Test no assets', () => {
    render(<AssetTable change={false} setChange={console.log} />);
    expect(screen.queryByRole("noAssets")?.textContent).toBe("You don't own any assets")
});

test('Asset Table Test 1 asset', async () => {
    render(<AssetTable change={false} setChange={console.log} />);
    await waitFor(() => {
        expect(screen.queryByRole("tickerHeader")?.textContent).to.include("Ticker")
        expect(screen.queryByRole("ticker")?.textContent).to.include("VTI")
        expect(screen.queryByRole("shares")?.textContent).to.include("5")
        expect(screen.queryByRole("costbasis")?.textContent).to.include("180")
        expect(screen.queryByRole("buy")?.textContent).to.include("2023-02-14")
    });
});

const asset: IAsset = {
    ticker: "SPY",
    shares: 7,
    costbasis: 210,
    buy: '2024-01-01'
}

test('Asset Row Test', () => {
    render(<AssetRow asset={asset} setChange={console.log} index={1} />);
    expect(screen.queryByText('SPY')).not.toBeNull();
    expect(screen.queryByText('7')).not.toBeNull();
    expect(screen.queryByText('$210')).not.toBeNull();
});

// ideally, i could test different inputs, such as invalid tickers
test('Asset Form Test', () => {
    render(<AssetForm setChange={console.log} />);
});