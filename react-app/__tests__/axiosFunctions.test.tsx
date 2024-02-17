// @vitest-environment jsdom
import { cleanup } from '@testing-library/react';
import { expect, it, afterEach, beforeAll } from 'vitest'
import { getOptions, getQuote, postAsset, login, getAssets } from '../src/components/AxiosFunctions';
import { IAsset } from '../src/interfaces';

beforeAll(async () => {await login("maxwell", "maxwell")});

afterEach(() => { cleanup() });

it('getOptions Test', async () => {
    const response = await getOptions()
    expect(response.data[0].ticker).to.include("AAPL")
});

it('getQuote Test', async () => {
    const response = await getQuote("AAPL")
    expect(response.data.c).to.toBeGreaterThan(0)
});

it('getAssets Test', async () => {
    const tokenResponse = await login("maxwell", "maxwell");
    const response = await getAssets()
    expect(response.data[0].ticker).to.include("SPY")
});

it('postAssets Test', async () => {
    const tokenResponse = await login("maxwell", "maxwell");
    const asset: IAsset = {
        ticker: "AAPL",
        shares: 4,
        costbasis: 200,
        buy: "2023-10-13"
    }
    const response = await postAsset(asset)
});