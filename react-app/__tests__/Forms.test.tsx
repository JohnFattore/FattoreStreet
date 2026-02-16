// @vitest-environment jsdom
import { screen, waitFor } from '@testing-library/react';
import { expect, describe, it } from 'vitest';
import '@testing-library/jest-dom';
import userEvent from '@testing-library/user-event';
import CreateAccountForm from '../src/components/CreateAccountForm';
import AssetForm from '../src/components/AssetForm';
import { renderWithProviders } from './testutils';

const authenticatedState = {
    user: { access: 'fake-token', refresh: 'fake-refresh', username: 'testUser' }
};

describe('CreateAccountForm', () => {
    it('does not render button when not authenticated', () => {
        renderWithProviders(<CreateAccountForm />);
        expect(screen.queryByText('Create New Account')).not.toBeInTheDocument();
    });

    it('opens modal and shows form fields', async () => {
        renderWithProviders(<CreateAccountForm />, { preloadedState: authenticatedState });

        const openButton = screen.getByText('Create New Account');
        expect(openButton).toBeInTheDocument();

        await userEvent.click(openButton);

        expect(await screen.findByPlaceholderText('Account Name')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
    });

    it('submits the form and closes modal', async () => {
        renderWithProviders(<CreateAccountForm />, { preloadedState: authenticatedState });

        await userEvent.click(screen.getByText('Create New Account'));

        const nameInput = await screen.findByPlaceholderText('Account Name');
        await userEvent.type(nameInput, 'My New IRA');
        await userEvent.click(screen.getByRole('button', { name: /create account/i }));

        await waitFor(() => {
            expect(screen.queryByPlaceholderText('Account Name')).not.toBeInTheDocument();
        });
    });
});

describe('AssetForm', () => {
    it('shows login prompt when not authenticated', () => {
        renderWithProviders(<AssetForm />);
        expect(screen.getByText(/Login to see portfolio/i)).toBeInTheDocument();
    });

    it('renders the Add Asset button when authenticated', () => {
        renderWithProviders(<AssetForm />, { preloadedState: authenticatedState });
        expect(screen.getByText('Add Asset')).toBeInTheDocument();
    });

    it('opens modal with form fields', async () => {
        renderWithProviders(<AssetForm />, { preloadedState: authenticatedState });

        await userEvent.click(screen.getByText('Add Asset'));

        expect(await screen.findByPlaceholderText('Ticker')).toBeInTheDocument();
        expect(screen.getByPlaceholderText('Shares')).toBeInTheDocument();
    });

    it('populates default ticker when provided', async () => {
        renderWithProviders(<AssetForm defaultTicker="AAPL" />, { preloadedState: authenticatedState });

        await userEvent.click(screen.getByText('Add Asset'));

        const tickerInput = await screen.findByPlaceholderText('Ticker');
        expect(tickerInput).toHaveValue('AAPL');
        expect(tickerInput).toBeDisabled();
    });
});
