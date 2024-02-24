// @vitest-environment jsdom
import { cleanup } from '@testing-library/react';
import { expect, it, afterEach, beforeAll } from 'vitest'
import { getOptions, getQuote, postAsset, login, getAssets, deleteAsset, getAsset } from '../src/components/AxiosFunctions';
import { IAsset } from '../src/interfaces';

beforeAll(async () => {await login("django", "django")});

afterEach(() => { cleanup() });

it('getOptions Test', async () => {
    const response = await getOptions()
    expect(response.data[0].ticker).to.equal("AAPL")
    expect(response.status).to.equal(200)
});

it('getQuote Test', async () => {
    const response = await getQuote("AAPL")
    expect(response.data.c).to.toBeGreaterThan(0)
    expect(response.status).to.equal(200)
});

it('getAssets Test', async () => {
    //await login("maxwell", "maxwell");
    const response = await getAssets()
    expect(response.data[0].ticker).to.equal("AAPL")
    expect(response.status).to.equal(200)
});

it('getAsset Test', async () => {
    const response = await getAsset(160)
    expect(response.data.ticker).toBeDefined
    expect(response.status).to.equal(200)
});

// post and delete tests together because this test affects the main database
it('postAsset and deleteAsset Test', async () => {
    //await login("maxwell", "maxwell");
    const asset: IAsset = {
        ticker: "AAPL",
        shares: 4,
        costbasis: 200,
        buy: "2023-10-13"
    }
    const postResponse = await postAsset(asset)
    expect(postResponse.status).to.equal(201)
    // DELETE TEST
    const deleteResponse = await deleteAsset(postResponse.data.id)
    expect(deleteResponse.status).to.equal(204)
});