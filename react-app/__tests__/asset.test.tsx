// @vitest-environment jsdom
import { render, screen, cleanup, waitFor, fireEvent, getByPlaceholderText } from '@testing-library/react';
import { expect, test, vi, afterEach } from 'vitest'
import React from "react";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import rootReducer from "../src/reducers/rootReducer"
import assetReducer from "../src/reducers/assetReducer"
import Home from '../src/pages/Home';
import '@testing-library/jest-dom';

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    localStorage.clear();
});

// mocked functions override real API calls
vi.mock('../src/components/axiosFunctions', () => ({
    __esModule: true,
    getAssets: vi.fn(() => Promise.resolve({ data: [{
        "id": 65,
        "shares": "1.00000",
        "cost_basis": "239.67",
        "sell_price": null,
        "buy_date": "2021-06-02",
        "sell_date": null,
        "user": 1,
        "asset_info": {
            "id": 25,
            "ticker": "MSFT",
            "short_name": "Microsoft Corporation",
            "long_name": "Microsoft Corporation",
            "type": "EQUITY",
            "exchange": "NASDAQ",
            "market": "us_market"
        },
        "snp500_buy_date": {
            "id": 609,
            "date": "2021-06-02",
            "price": "398.28"
        },
        "snp500_sell_date": null
    }] })),
    getQuote: vi.fn(() => new Promise((resolve) => resolve({ data: { c: 200, d: 3, dp: 1.5 } }))),
    postAsset: vi.fn(() => new Promise((resolve) => resolve({ status: 201 }))),
    getRestaurants: vi.fn(() => Promise.resolve({
        data: [{
            yelp_id: "bBDDEgkFA1Otx9Lfe7BZUQ",
            name: "Sonic Drive-In",
            address: "2312 Dickerson Pike",
            state: "TN",
            city: "Nashville",
            latitude: "36.20810240",
            longitude: "-86.76816960",
            categories: "Ice Cream & Frozen Yogurt, Fast Food, Burgers, Restaurants, Food",
            stars: 1.5,
            review_count: 10,
            id: 104585
        }]
    }))
}));


const mockStore = configureStore({
    reducer: assetReducer,
});


test('Home Test', async () => {
    render(<Home />);
    screen.debug();
});


test('Portfolio Test', async () => {
    render(
        <Provider store={mockStore}>
            <Home />
        </Provider>);
    screen.debug();
});