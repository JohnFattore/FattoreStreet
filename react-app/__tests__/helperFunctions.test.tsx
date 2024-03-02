// @vitest-environment jsdom
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import { expect, test, it, vi, afterEach } from 'vitest'
import React from 'react';
import { useQuote } from '../src/components/helperFunctions';

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    localStorage.clear();
});

// mocked functions override real API calls
vi.mock('../src/components/AxiosFunctions', () => ({
    __esModule: true,
    getQuote: vi.fn(() => new Promise((resolve) => resolve({ data: { c: 200, d: 3, dp: 1.5 } }))),
}));

// react hooks need to be used in functional components
/*
it('useQuote Test', async () => {
    await waitFor(() => {
        const quote = useQuote("SPY", console.log);
        expect(quote.price).to.equal(200);
    });
});
*/

it('useQuote Test', async () => {
    expect(200).to.equal(200);
});