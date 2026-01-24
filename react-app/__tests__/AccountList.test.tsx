// @vitest-environment jsdom
import { screen, cleanup } from '@testing-library/react';
import { expect, test, vi, afterEach } from 'vitest'
import '@testing-library/jest-dom';
import AccountList from '../src/components/AccountList';
import { renderWithProviders } from './testutils';

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

test('AccountList renders and calculates balances correctly', async () => {
    renderWithProviders(<AccountList />, {
        preloadedState: {
            user: { access: 'fake-token', refresh: 'fake-refresh', username: 'testuser' }
        }
    });

    // Check for account names
    expect(await screen.findByText('Taxable Brokerage')).toBeInTheDocument();
    expect(await screen.findByText('Roth IRA')).toBeInTheDocument();

    // Check for calculated balances and daily changes (using findByText to wait for assets/infos)
    // Account 1 Balance: 10 shares * $500 = $5,000.00
    expect(await screen.findByText('$5,000.00')).toBeInTheDocument();

    // Account 2 Balance: 20 shares * $200 = $4,000.00
    expect(await screen.findByText('$4,000.00')).toBeInTheDocument();

    // Account 1 Day Change: $98.04 (2.00%)
    expect(await screen.findByText(/\$98\.04 \(2\.00%\)/)).toBeInTheDocument();

    // Account 2 Day Change: -$40.40 (-1.00%)
    expect(await screen.findByText(/-\$40\.40 \(-1\.00%\)/)).toBeInTheDocument();
});
