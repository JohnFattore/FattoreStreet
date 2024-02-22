// @vitest-environment jsdom
import { render, screen, cleanup, waitFor, fireEvent, getByPlaceholderText } from '@testing-library/react';
import { expect, test, vi, afterEach } from 'vitest'
import React from 'react';
import Portfolio from '../src/pages/Portfolio'
import AssetTable from '../src/components/AssetTable'
import { getAssets, getQuote, postAsset } from '../src/components/AxiosFunctions';

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

// mocked functions override real API calls
vi.mock('../src/components/AxiosFunctions', () => ({
    __esModule: true,
    getAssets: vi.fn(() => new Promise((resolve) => resolve({ data: [{ ticker: "VTI", shares: 5, costbasis: 180, buy: '2023-02-14' }] }))),
    getQuote: vi.fn(() => new Promise((resolve) => resolve({ data: { c: 200, d: 3, dp: 1.5 } }))),
    postAsset: vi.fn(() => new Promise((resolve) => resolve({ status: 201 }))),
}));

// without waitFor, component renders with empty API calls
test('Portfolio Test no assets Rebder', async () => {
    render(<Portfolio />);
    await waitFor(() => {
        expect(getAssets).toBeCalled()
        expect(screen.queryByRole('assetTableHeader')?.textContent).to.include("User's Portfolio");
    });
});

test('Portfolio Test 1 asset', async () => {
    render(<Portfolio/>);
    await waitFor(() => {
        expect(getAssets).toBeCalled()
        expect(screen.queryByRole("tickerHeader")?.textContent).to.equal("Ticker")
        expect(screen.queryByRole("ticker")?.textContent).to.equal("VTI")
        expect(screen.queryByRole("shares")?.textContent).to.equal("5")
        expect(screen.queryByRole("costbasis")?.textContent).to.equal("$180")
        expect(screen.queryByRole("buy")?.textContent).to.equal("2023-02-14")
    });
});

test('Portfolio Test form blank submit', async () => {
    render(<Portfolio/>);
    fireEvent.input(screen.getByPlaceholderText("Ticker"), { target: { value: "VTI", }, })
    fireEvent.input(screen.getByPlaceholderText("Shares"), { target: { value: "5", }, })
    fireEvent.input(screen.getByPlaceholderText("Cost Basis"), { target: { value: "200", }, })

    fireEvent.submit(screen.getByRole("button"));
    // this line
    expect(await screen.findAllByRole("buyDateError")).toHaveLength(1);
    expect(screen.queryByRole("buyDateError")?.textContent).to.include("Error");
    expect(screen.queryByRole("tickerError")?.textContent).not.toBeDefined();
    expect(screen.queryByRole("sharesError")?.textContent).not.toBeDefined();
    expect(screen.queryByRole("costBasisError")?.textContent).not.toBeDefined();
});

test('Portfolio Test form full submit', async () => {
    render(<Portfolio/>);
    fireEvent.input(screen.getByPlaceholderText("Ticker"), { target: { value: "VTI", }, })
    fireEvent.input(screen.getByPlaceholderText("Shares"), { target: { value: "5", }, })
    fireEvent.input(screen.getByPlaceholderText("Cost Basis"), { target: { value: "200", }, })
    fireEvent.input(screen.getByPlaceholderText("Buy Date"), { target: { value: "2024-01-11", }, })
    fireEvent.submit(screen.getByRole("button"));

    await waitFor(async () => {
        expect(getQuote).toBeCalled()
        expect(postAsset).toBeCalled()
        expect(screen.queryByRole("successMessage")?.textContent).to.include("of");
    });
    expect(screen.queryByRole("tickerError")?.textContent).not.toBeDefined();
    expect(screen.queryByRole("sharesError")?.textContent).not.toBeDefined();
    expect(screen.queryByRole("costBasisError")?.textContent).not.toBeDefined();
    expect(screen.queryByRole("buyDateError")?.textContent).not.toBeDefined();
});