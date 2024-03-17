// @vitest-environment jsdom
import { cleanup } from '@testing-library/react';
import { expect, it, afterEach, beforeAll } from 'vitest'
import { getOptions, getQuote, postAsset, login, getAssets, deleteAsset, getAsset, getSelections, getCompanyProfile2, postSelection, deleteSelection } from '../src/components/axiosFunctions';
import { IAsset, ISelection } from '../src/interfaces';

beforeAll(async () => {await login("django", "django")});

afterEach(() => { cleanup() });

it('getQuote Test', async () => {
    const response = await getQuote("AAPL")
    expect(response.data.c).to.toBeGreaterThan(0)
    expect(response.status).to.equal(200)
});

it('getCompanyProfile2 Test', async () => {
    const response = await getCompanyProfile2("AAPL")
    expect(response.data.country).to.equal("US")
    expect(response.status).to.equal(200)
});


it('getAssets Test', async () => {
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
    const asset: IAsset = {
        ticker: "AAPL",
        shares: 4,
        costbasis: 200,
        buy: "2023-10-13",
        id: 1
    }
    const postResponse = await postAsset(asset)
    expect(postResponse.status).to.equal(201)
    // DELETE TEST
    const deleteResponse = await deleteAsset(postResponse.data.id)
    expect(deleteResponse.status).to.equal(204)
});

it('getOptions Test', async () => {
    const response = await getOptions(0)
    expect(response.data[0].ticker).to.equal("C")
    expect(response.status).to.equal(200)
});

it('getSelections Test', async () => {
    const response = await getSelections(0)
    console.log(response)
    expect(response.data[0].user).to.equal(3)
    expect(response.status).to.equal(200)
});

// post and delete tests together because this test affects the main database
it('postSelection and deleteSelection Test', async () => {
    const selection: ISelection = {
        option: 1,
        user: 1,
        id: 1
    }
    const postResponse = await postSelection(selection)
    expect(postResponse.status).to.equal(201)
    // DELETE TEST
    const deleteResponse = await deleteSelection(postResponse.data.id)
    expect(deleteResponse.status).to.equal(204)
});