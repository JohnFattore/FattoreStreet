// @vitest-environment jsdom
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import { expect, test, it, vi, afterEach } from 'vitest'
import React from 'react';
import Allocation from "../src/pages/Allocation";

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    localStorage.clear();
});
// mocked functions override real API calls
vi.mock('../src/components/axiosFunctions', () => ({
    __esModule: true,
    getAssets: vi.fn(() => new Promise((resolve) => resolve({ data: [{ ticker: "VTI", shares: 5, costbasis: 180, buy: '2023-02-14' }] }))),
    getQuote: vi.fn(() => new Promise((resolve) => resolve({ data: { c: 200, d: 3, dp: 1.5 } }))),
}));

// without waitFor, component renders with empty API calls
test('Allocation Page Test, No Assets', () => {
    render(<Allocation />);
    expect(screen.queryByRole('pageHeader')?.textContent).to.equal("User's Allocation of Assets");
});

test('Allocation Page Test, 1 Asset', async () => {
    render(<Allocation />);
    await waitFor(() => {
        expect(screen.queryByRole('tickerHeader')?.textContent).to.equal("Ticker");
        expect(screen.queryByRole('ticker')?.textContent).to.equal("VTI");
        expect(screen.queryByRole('shares')?.textContent).to.equal("5.00");
        // im not sure how to reference the Promise the mock functions are returning
        expect(screen.queryByRole('price')?.textContent).to.equal("$" + (5 * 200).toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }));
    });
});