// @vitest-environment jsdom
import { render, screen, cleanup, waitFor, fireEvent, getByPlaceholderText } from '@testing-library/react';
import { expect, test, vi, afterEach } from 'vitest'
import React, { useState } from 'react';
import AssetForm from '../src/components/AssetForm'
import AssetTable from '../src/components/AssetTable'
import AssetRow from '../src/components/AssetRow'
import { IAsset, IMessage } from '../src/interfaces';
import { getAssets, getQuote, postAsset } from '../src/components/AxiosFunctions';

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

const message: IMessage = {text: "", type: ""}
const asset: IAsset = { ticker: "SPY", shares: 7, costbasis: 210, buy: '2024-01-01', id: 1 }

// mocked functions override real API calls
vi.mock('../src/components/AxiosFunctions', () => ({
    __esModule: true,
    getAssets: vi.fn(() => new Promise((resolve) => resolve({ data: [{ ticker: "VTI", shares: 5, costbasis: 180, buy: '2023-02-14' }] }))),
    getQuote: vi.fn(() => new Promise((resolve) => resolve({ data: { c: 200, d: 3, dp: 1.5 } }))),
    postAsset: vi.fn(() => new Promise((resolve) => resolve({ status: 201 }))),
}));

//console.log is just a random function passed for the sake of testing
test('Asset Table Test no assets', async () => {
    render(<AssetTable setMessage={console.log} assets={[]} setAssets={console.log} />);
    await waitFor(() => {
        expect(getAssets).toBeCalled()
        expect(screen.queryByRole("noAssets")?.textContent).to.equal("You don't own any assets")
    });
});

// setAssets being console.log sorta messes this test up, more rigirous testing in integration
test('Asset Table Test 1 asset', async () => {
    render(<AssetTable setMessage={console.log} assets={[asset]} setAssets={console.log}/>);
    await waitFor(() => {
        expect(screen.queryByRole("ticker")?.textContent).to.include("SPY")
        expect(screen.queryByRole("tickerHeader")?.textContent).to.include("Ticker")
        expect(getAssets).toBeCalled()
        expect(screen.queryByRole("shares")?.textContent).to.include("7")
        expect(screen.queryByRole("costbasis")?.textContent).to.include("210")
        expect(screen.queryByRole("buy")?.textContent).to.include("2024-01-01")
    });
});

test('Asset Row Test', () => {
    render(<AssetRow asset={asset} setMessage={console.log} assets={[asset]} setAssets={console.log} />);
    expect(getQuote).toBeCalled()
    expect(screen.queryByRole("ticker")?.textContent).to.equal(asset.ticker);
    expect(screen.queryByRole("shares")?.textContent).to.equal(asset.shares.toString());
    expect(screen.queryByRole("costbasis")?.textContent).to.equal("$" + asset.costbasis.toString());
});

test('Asset Form Test black submit', async () => {
    render(<AssetForm setMessage={console.log} assets={[asset]} setAssets={console.log}/>);
    fireEvent.submit(screen.getByRole("button"));
    // this line
    expect(await screen.findAllByRole("tickerError")).toHaveLength(1);
    expect(getQuote).not.toBeCalled()
    expect(screen.queryByRole("tickerError")?.textContent).to.include("Error");
    expect(screen.queryByRole("sharesError")?.textContent).to.include("Error");
    expect(screen.queryByRole("costBasisError")?.textContent).to.include("Error");
    expect(screen.queryByRole("buyDateError")?.textContent).to.include("Error");
});

test('Asset Form Test cost basis blank submit', async () => {
    render(<AssetForm setMessage={console.log} assets={[asset]} setAssets={console.log}/>);
    expect(screen.queryByRole("purchase")?.textContent).not.toBeDefined();

    fireEvent.input(screen.getByPlaceholderText("Ticker"), { target: { value: "VTI", }, })
    fireEvent.input(screen.getByPlaceholderText("Shares"), { target: { value: "5", }, })
    fireEvent.input(screen.getByPlaceholderText("Cost Basis"), { target: { value: "200", }, })

    fireEvent.submit(screen.getByRole("button"));

    expect(await screen.findAllByRole("buyDateError")).toHaveLength(1);
    expect(screen.queryByRole("buyDateError")?.textContent).to.include("Error");
    expect(screen.queryByRole("tickerError")?.textContent).not.toBeDefined();
    expect(screen.queryByRole("sharesError")?.textContent).not.toBeDefined();
    expect(screen.queryByRole("costBasisError")?.textContent).not.toBeDefined();
});

test('Asset Form Test full submit', async () => {
    render(<AssetForm setMessage={console.log} assets={[asset]} setAssets={console.log}/>);

    fireEvent.input(screen.getByPlaceholderText("Ticker"), { target: { value: "VTI", }, });
    fireEvent.input(screen.getByPlaceholderText("Shares"), { target: { value: "5", }, });
    fireEvent.input(screen.getByPlaceholderText("Cost Basis"), { target: { value: "200", }, });
    fireEvent.input(screen.getByPlaceholderText("Buy Date"), { target: { value: "2024-01-11", }, });

    fireEvent.submit(screen.getByRole("button"));

    await waitFor(async () => {
        expect(getQuote).toBeCalled()
        expect(postAsset).toBeCalled()
        // message in included in Portfolio
        //expect(screen.queryByRole("successMessage")?.textContent).to.include("of");
    });
    expect(screen.queryByRole("tickerError")?.textContent).not.toBeDefined();
    expect(screen.queryByRole("sharesError")?.textContent).not.toBeDefined();
    expect(screen.queryByRole("costBasisError")?.textContent).not.toBeDefined();
    expect(screen.queryByRole("buyDateError")?.textContent).not.toBeDefined();
});