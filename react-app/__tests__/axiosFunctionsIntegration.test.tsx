// @vitest-environment jsdom
import { cleanup } from '@testing-library/react';
import { expect, it, afterEach, beforeAll } from 'vitest'
import { getOptions, getQuote, postAsset, login, getAssets, deleteAsset, getAsset } from '../src/components/axiosFunctions';
import { IAsset } from '../src/interfaces';

beforeAll(async () => {await login("maxwell", "maxwell")});

afterEach(() => { cleanup() });

it('postAsset, getAssets, deleteAsset Test', async () => {
    const asset: IAsset = {
        ticker: "AAPL",
        shares: 4,
        costbasis: 200,
        buy: "2023-10-13",
        id: 1
    }
    const postResponse = await postAsset(asset)
    expect(postResponse.status).to.equal(201)
    // GET TEST
    const getResponse = await getAsset(postResponse.data.id)
    expect(getResponse.status).to.equal(200)
    // DELETE TEST
    const deleteResponse = await deleteAsset(postResponse.data.id)
    expect(deleteResponse.status).to.equal(204)
});