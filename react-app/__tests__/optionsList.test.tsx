// @vitest-environment jsdom
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import { expect, test, it, vi, afterEach } from 'vitest'
import React from 'react';
import OptionsList from '../src/components/OptionsList'
import { IOption } from '../src/interfaces';

afterEach(() => { cleanup() });

// mock function replaces actual functions Promise
// removing this mock function causes the actual function to fire
vi.mock('../src/components/AxiosFunctions', () => ({
    __esModule: true,
    getOptions: vi.fn(() => new Promise((resolve) => resolve({ data: [{ ticker: "AAPL", sunday: "2024-02-18" }] }))),
}));
 
it('OptionsList Test', async () => {
    render(<OptionsList />);
    // must wait for async function getOptions is finished before making asserts
    await waitFor(() => {
        expect(screen.queryByRole('ticker')?.textContent).to.include("AAPL");
        expect(screen.queryByRole('sunday')?.textContent).to.include("2024-02-18");
        screen.debug();
    });
});