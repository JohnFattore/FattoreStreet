// @vitest-environment jsdom
import { render, screen, cleanup, waitFor, fireEvent } from '@testing-library/react';
import { expect, test, it, vi, afterEach } from 'vitest'
import React from 'react';
import Wallstreet from '../src/pages/WallStreet'

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    localStorage.clear();
});
// mock function replaces actual functions Promise
// removing this mock function causes the actual function to fire
vi.mock('../src/components/axiosFunctions', () => ({
    __esModule: true,
    getOptions: vi.fn(() => new Promise((resolve) => resolve({ data: [{ ticker: "AAPL", sunday: "2024-03-03", id: 1 }] }))),
    getSelections: vi.fn(() => new Promise((resolve) => resolve({ data: [{ option: 1, sunday: "2024-03-03", user: 1, id: 1 }] }))),
    postSelection: vi.fn(() => new Promise((resolve) => resolve({ data: [{ id: 2 }] }))),
    deleteSelection: vi.fn(() => new Promise((resolve) => resolve({ status: 201 }))),
}));

it('Wallstreet Test Render', async () => {
    render(<Wallstreet />);
    await waitFor(() => {
        expect(screen.queryByRole('selectionTicker')?.textContent).to.include("AAPL");
        expect(screen.queryByRole('optionTicker')?.textContent).to.include("AAPL");
        expect(screen.queryByRole('selectionSunday')?.textContent).to.include("2024-03-03");
        expect(screen.queryByRole('optionSunday')?.textContent).to.include("2024-03-03");
    });
});

// need test for adding additional selection
it('Wallstreet Test add duplicate selection', async () => {
    render(<Wallstreet />);
    await waitFor(() => {
        fireEvent.click(screen.getByRole("optionTicker"));
        expect(screen.queryByRole("selectionTicker")?.textContent).to.equal("AAPL");
        //expect(screen.queryByRole('optionTicker')?.textContent).to.include("AAPL");
        expect(screen.queryByRole("selectionSunday")?.textContent).to.equal("2024-03-03");
        expect(screen.queryByRole("message")?.textContent).to.include("selection");
        //expect(screen.queryByRole('optionSunday')?.textContent).to.include("2024-03-03");
        screen.debug()
    });
});

it('Wallstreet Test delete selection', async () => {
    render(<Wallstreet />);
    await waitFor(async () => {
        fireEvent.click(screen.getByRole("selectionTicker"));
        expect(screen.getByRole("selectionTicker")).not.toBeDefined;
        expect(screen.queryByRole('optionTicker')?.textContent).to.include("AAPL");
        //expect(screen.getByRole("selectionSunday")).not.toBeDefined;
        expect(screen.queryByRole('optionSunday')?.textContent).to.include("2024-03-03");
        expect(screen.queryByRole("message")?.textContent).toBeDefined
    });
});