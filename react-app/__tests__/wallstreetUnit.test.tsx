// @vitest-environment jsdom
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import { expect, test, it, vi, afterEach } from 'vitest'
import React from 'react';
import OptionTable from '../src/components/OptionTable'
import OptionRow from '../src/components/OptionRow';
import SelectionTable from '../src/components/SelectionTable';
import SelectionRow from '../src/components/SelectionRow';
import { IOption, ISelection } from '../src/interfaces';

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    localStorage.clear();
});
// mock function replaces actual functions Promise
// removing this mock function causes the actual function to fire
vi.mock('../src/components/AxiosFunctions', () => ({
    __esModule: true,
    getOptions: vi.fn(() => new Promise((resolve) => resolve({ data: [{ ticker: "AAPL", sunday: "2024-02-18", id: 1 }] }))),
    getSelections: vi.fn(() => new Promise((resolve) => resolve({ data: [{ option: 1, sunday: "2024-02-18", id: 2 }] }))),

}));

const option: IOption = {
    ticker: "SPY",
    sunday: "2024-03-03",
    id: 1
}


it('OptionRow Test Render', async () => {
    render(<OptionRow option={option} selections={[]} selectionsDispatch={console.log} setMessage={console.log} />);
    await waitFor(() => {
        expect(screen.queryByRole('optionTicker')?.textContent).to.include(option.ticker);
        expect(screen.queryByRole('optionSunday')?.textContent).to.include(option.sunday);
    });
});

it('OptionTable Test Render', async () => {
    render(<OptionTable setMessage={console.log} options={[option]} selections={[]} optionsDispatch={console.log} selectionsDispatch={console.log} />);
    await waitFor(() => {
        expect(screen.queryByRole('optionTicker')?.textContent).to.include(option.ticker);
        expect(screen.queryByRole('optionSunday')?.textContent).to.include(option.sunday);
    });
});

it('OptionTable Test 3 options', async () => {
    render(<OptionTable setMessage={console.log} options={[option, option, option]} selections={[]} optionsDispatch={console.log} selectionsDispatch={console.log} />);
    // must wait for async function getOptions is finished before making asserts
    expect(await screen.findAllByRole("optionTicker")).toHaveLength(3);
});

const selection: ISelection = {
    option: 1,
    sunday: "2024-03-03",
    user: 1,
    id: 1
}

it('SelectionRow Test Render', async () => {
    render(<SelectionRow selection={selection} setMessage={console.log} options={[option]} selectionsDispatch={console.log} />);
    await waitFor(() => {
        expect(screen.queryByRole('selectionTicker')?.textContent).to.include(option.ticker);
    });
});

it('SelectionTable Test Render', async () => {
    render(<SelectionTable selections={[selection]} selectionsDispatch={console.log} setMessage={console.log} options={[option]} />);
    await waitFor(() => {
        expect(screen.queryByRole('selectionTicker')?.textContent).to.include(option.ticker);
        expect(screen.queryByRole('selectionSunday')?.textContent).to.include(selection.sunday);
        //expect(screen.queryByRole('sunday')?.textContent).to.include(option.sunday);
    });
});

it('SelectionTable Test 3 Selections', async () => {
    render(<SelectionTable  selections={[selection, selection, selection]} selectionsDispatch={console.log} setMessage={console.log} options={[option]} />);
    // must wait for async function getOptions is finished before making asserts
    expect(await screen.findAllByRole("selectionTicker")).toHaveLength(3);
    expect(await screen.findAllByRole("selectionSunday")).toHaveLength(3);
});