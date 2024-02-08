// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { expect, test, vi, afterEach } from 'vitest'
import React from 'react';
import Portfolio from '../src/pages/Portfolio'
import AssetForm from '../src/components/AssetForm'
import AssetTable from '../src/components/AssetTable'
import AssetRow from '../src/components/AssetRow'
import { ENVContext } from '../src/components/ENVContext';
import { IAsset } from '../src/interfaces';

afterEach(() => { document.body.innerHTML = ''; });
const ENV = {
    finnhubURL: "https://finnhub.io/api/v1/",
    djangoURL: "http://127.0.0.1:8000/portfolio/api/",
    finnhubKey: "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"
};

test('Portfolio Page Test', () => {
    render(
        <ENVContext.Provider value={ENV}>
            <Portfolio />
        </ENVContext.Provider>
    );
    screen.debug()
    expect(screen.queryByRole('assetTableHeader')?.textContent).to.include("User's Portfolio");
});

test('Asset Table Test', () => {
    render(
        <ENVContext.Provider value={ENV}>
            <AssetTable change={false} setChange={console.log} />
        </ENVContext.Provider>
    );
});

const asset: IAsset = {
    ticker: "SPY",
    shares: 7,
    costbasis: 210,
    buy: '2024-01-01'
}

test('Asset Row Test', () => {
    render(
        <ENVContext.Provider value={ENV}>
            <AssetRow asset={asset} setChange={console.log}/>
        </ENVContext.Provider>
    );
    expect(screen.queryByText('SPY')).not.toBeNull();
    expect(screen.queryByText('7')).not.toBeNull();
    expect(screen.queryByText('$210')).not.toBeNull();
});

test('Asset Form Test', () => {
    render(
        <ENVContext.Provider value={ENV}>
            <AssetForm setChange={console.log}/>
        </ENVContext.Provider>
    );
});