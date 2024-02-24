// @vitest-environment jsdom

import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { expect, test, vi, afterEach } from 'vitest'
import React from 'react';
import Watchlist from '../src/pages/WatchList'

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

vi.mock('../src/components/AxiosFunctions', () => ({
    __esModule: true,
    getAssets: vi.fn(() => new Promise((resolve) => resolve({ data: [{ ticker: "VTI", shares: 5, costbasis: 180, buy: '2023-02-14' }] }))),
    getQuote: vi.fn(() => new Promise((resolve) => resolve({ data: { c: 200, d: 3, dp: 1.5 } }))),
    login: vi.fn(() => new Promise((resolve) => resolve({ data: { access: "maxwellKEY", refresh: "spikeKEY" } }))),
}));

test('Watchlist Test Render', () => {
    render(<Watchlist/>); 
});

test('Watchlist Test existing ticker submit', async () => {
    render(<Watchlist/>); 
    fireEvent.input(screen.getByPlaceholderText("Enter Ticker Here"), { target: { value: "VTI", }, });
    fireEvent.submit(screen.getByRole("button"));
  
      await waitFor(async () => {
        expect(screen.queryByRole("tickerError")?.textContent).toBeDefined();
      });
});

test('Watchlist Test failed submit', async () => {

    render(<Watchlist/>); 
    fireEvent.submit(screen.getByRole("button"));

    await waitFor(async () => {
      expect(screen.queryByRole("tickerError")?.textContent).toBeDefined();
      });
});

test('Watchlist Test f submit', async () => {
    render(<Watchlist/>); 
    fireEvent.input(screen.getByPlaceholderText("Enter Ticker Here"), { target: { value: "C", }, });
    fireEvent.submit(screen.getByRole("button"));
  
      await waitFor(async () => {
        expect(screen.queryByRole("tickerError")?.textContent).not.toBeDefined();
        expect(screen.queryAllByRole("ticker")).toHaveLength(3);
        expect(screen.queryAllByRole("percentChange")).toHaveLength(3);
        expect(screen.queryAllByRole("delete")).toHaveLength(3);
      });

});