// @vitest-environment jsdom
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import { expect, test, it, vi, afterEach } from 'vitest'
import React from 'react';
import AllocationRow from "../src/components/AllocationRow";
import AllocationTable from "../src/components/AllocationTable";
import { IAllocation } from '../src/interfaces';

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    localStorage.clear();
});

// mocked functions override real API calls
vi.mock('../src/components/AxiosFunctions', () => ({
    __esModule: true,
    getAssets: vi.fn(() => new Promise((resolve) => resolve({ data: [{ ticker: "VTI", shares: 5, costbasis: 180, buy: '2023-02-14' }] }))),
    getQuote: vi.fn(() => new Promise((resolve) => resolve({ data: { c: 200, d: 3, dp: 1.5 } }))),
}));

// without waitFor, component renders with empty API calls

test('Allocation Table Test, No Assets', () => {
    render(<AllocationTable setMessage={console.log} />);
    expect(screen.queryByRole('noAssets')?.textContent).to.include("You don't own any assets");
});


test('Allocation Table Test, 1 Asset', async () => {
    render(<AllocationTable setMessage={console.log} />);
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

const allocation: IAllocation = {
    ticker: "SPY",
    shares: 5
}
it('Allocation Row Test', async () => {
    render(<AllocationRow allocation={allocation} setMessage={console.log} />);
    await waitFor(() => {
        expect(screen.queryByRole('ticker')?.textContent).to.include(allocation.ticker);
        expect(screen.queryByRole('shares')?.textContent).to.include(allocation.shares);
        expect(screen.queryByRole('price')?.textContent).to.equal("$" + (allocation.shares * 200).toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }));
    });

});