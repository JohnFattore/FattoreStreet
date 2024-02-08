// @vitest-environment jsdom
import { render, screen, cleanup } from '@testing-library/react';
import { expect, test, it, vi, afterEach } from 'vitest'
import React from 'react';
import Allocation from "../src/pages/Allocation";
import AllocationRow from "../src/components/AllocationRow";
import AllocationTable from "../src/components/AllocationTable";
import { IAllocation } from '../src/interfaces';

afterEach(() => { cleanup(); });

test('Allocation Page Test, No Assets', () => {
    render(<Allocation />);
});

test('Allocation Table Test, 1 Asset', () => {
    render(<AllocationTable />);
    //expect(screen.queryByRole('noAssets')?.textContent).to.include("You don't own any assets");
});

test('Allocation Table Test, No Assets', () => {
    render(<AllocationTable />);
    expect(screen.queryByRole('noAssets')?.textContent).to.include("You don't own any assets");
});

// silver standard test
const allocation: IAllocation = {
    ticker: "SPY",
    shares: 5
}

test('Allocation Row Test', () => {
    render(<AllocationRow allocation={allocation} />);
    expect(screen.queryByRole('ticker')?.textContent).to.include(allocation.ticker);
    expect(screen.queryByRole('shares')?.textContent).to.include(allocation.shares);
    expect(screen.queryByRole('price')?.textContent).to.include('$');
});

it('Allocation Row Mock Finnhub Test', async () => {
    render(<AllocationRow allocation={allocation} />);
    expect(screen.queryByRole('ticker')?.textContent).to.include(allocation.ticker);
    expect(screen.queryByRole('shares')?.textContent).to.include(allocation.shares);
    expect(screen.queryByRole('price')?.textContent).to.include('$');
});