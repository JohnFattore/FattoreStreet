// @vitest-environment jsdom

import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { expect, test, vi, afterEach } from 'vitest'
import React from 'react';
import Watchlist from '../src/pages/WatchList'

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  localStorage.clear();
});

vi.mock('../src/components/axiosFunctions', () => ({
    __esModule: true,
    getAssets: vi.fn(() => new Promise((resolve) => resolve({ data: [{ ticker: "VTI", shares: 5, costbasis: 180, buy: '2023-02-14' }] }))),
    getQuote: vi.fn(() => new Promise((resolve) => resolve({ data: { c: 200, d: 3, dp: 1.5 } }))),
    login: vi.fn(() => new Promise((resolve) => resolve({ data: { access: "maxwellKEY", refresh: "spikeKEY" } }))),
    getCompanyProfile2: vi.fn(() => new Promise((resolve) => resolve({ data: { marketCapitalization: 1415993 } }))),
}));

test('Watchlist Test Render', () => {
    render(<Watchlist/>); 
    expect(screen.queryAllByRole("ticker")).toHaveLength(2);
    expect(screen.queryAllByRole("percentChange")).toHaveLength(2);
    expect(screen.queryAllByRole("delete")).toHaveLength(2);
});

// new users get VTI and SPY in their watchlist
test('Watchlist Test successful submit', async () => {
  render(<Watchlist/>); 
  fireEvent.input(screen.getByPlaceholderText("Enter Ticker Here"), { target: { value: "C", }, });
  fireEvent.submit(screen.getByRole("button"));

    await waitFor(async () => {
      expect(screen.queryAllByRole("ticker")).toHaveLength(3);
      expect(screen.queryAllByRole("percentChange")).toHaveLength(3);
      expect(screen.queryAllByRole("delete")).toHaveLength(3);
      expect(screen.queryByRole("tickerError")?.textContent).not.toBeDefined();
    });

});

test('Watchlist Test failed submit', async () => {
    render(<Watchlist/>); 
    fireEvent.submit(screen.getByRole("button"));

    await waitFor(async () => {
      expect(screen.queryByRole("tickerError")?.textContent).toBeDefined();
      });
});

// this one needs to add "VTI" before the exisitng ticker error will appear
test('Watchlist Test existing ticker submit', async () => {
  render(<Watchlist/>); 
  fireEvent.input(screen.getByPlaceholderText("Enter Ticker Here"), { target: { value: "VTI", }, });
  fireEvent.submit(screen.getByRole("button"));

    await waitFor(async () => {
      expect(screen.queryByRole("message")?.textContent).toBeDefined();
    });
});