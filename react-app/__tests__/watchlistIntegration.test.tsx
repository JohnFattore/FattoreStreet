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

test('Login form Test Render', () => {
    render(<Watchlist/>);
});