// @vitest-environment jsdom
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import { expect, test, it, vi, afterEach } from 'vitest'
import React from 'react';
import OptionTable from '../src/components/OptionTable'
import OptionRow from '../src/components/OptionRow';
import SelectionTable from '../src/components/SelectionTable';
import SelectionRow from '../src/components/SelectionRow';
import { IOption, ISelection } from '../src/interfaces';
import OptionSelectionRow from '../src/components/OptionSelectionRow';
import OptionSelectionTable from '../src/components/OptionSelectionTable';

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    localStorage.clear();
});
// mock function replaces actual functions Promise
// removing this mock function causes the actual function to fire
vi.mock('../src/components/axiosFunctions', () => ({
    __esModule: true,
    getOptions: vi.fn(() => new Promise((resolve) => resolve({ data: [{ ticker: "AAPL", sunday: "2024-02-18", id: 1 }] }))),
    getSelections: vi.fn(() => new Promise((resolve) => resolve({ data: [{ option: 1, sunday: "2024-02-18", id: 2 }] }))),
    getQuote: vi.fn(() => new Promise((resolve) => resolve({ data: { c: 200, d: 3, dp: 1.5 } }))),
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

it('OptionTable Test 0 option', async () => {
    render(<OptionTable setMessage={console.log} options={[]} selections={[]} optionsDispatch={console.log} selectionsDispatch={console.log} week={0}/>);
    await waitFor(() => {
        expect(screen.queryByRole('noOptions')?.textContent).to.include("options");
    });
});

it('OptionTable Test 1 option', async () => {
    render(<OptionTable setMessage={console.log} options={[option]} selections={[]} optionsDispatch={console.log} selectionsDispatch={console.log} week={0}/>);
    await waitFor(() => {
        expect(screen.queryByRole('optionTicker')?.textContent).to.include(option.ticker);
        expect(screen.queryByRole('optionSunday')?.textContent).to.include(option.sunday);
    });
});

it('OptionTable Test 3 options', async () => {
    render(<OptionTable setMessage={console.log} options={[option, option, option]} selections={[]} optionsDispatch={console.log} selectionsDispatch={console.log} week={0}/>);
    // must wait for async function getOptions is finished before making asserts
    expect(await screen.findAllByRole("optionTicker")).toHaveLength(3);
});

const selection: ISelection = {
    option: 1,
    user: 1,
    id: 1
}

it('SelectionRow Test Render', async () => {
    render(<SelectionRow selection={selection} setMessage={console.log} options={[option]} selectionsDispatch={console.log} />);
    await waitFor(() => {
        expect(screen.queryByRole('selectionTicker')?.textContent).to.include(option.ticker);
    });
});

it('SelectionTable Test 0 Selection', async () => {
    render(<SelectionTable selections={[]} selectionsDispatch={console.log} setMessage={console.log} options={[option]} week={0}/>);
    await waitFor(() => {
        expect(screen.queryByRole('noSelections')?.textContent).to.include("selections");
    });
});

it('SelectionTable Test 1 Selection', async () => {
    render(<SelectionTable selections={[selection]} selectionsDispatch={console.log} setMessage={console.log} options={[option]} week={0}/>);
    await waitFor(() => {
        expect(screen.queryByRole('selectionTicker')?.textContent).to.include(option.ticker);
        //expect(screen.queryByRole('selectionSunday')?.textContent).to.include(selection.sunday);
        //expect(screen.queryByRole('sunday')?.textContent).to.include(option.sunday);
    });
});

it('SelectionTable Test 3 Selections', async () => {
    render(<SelectionTable  selections={[selection, selection, selection]} selectionsDispatch={console.log} setMessage={console.log} options={[option]} week={0}/>);
    // must wait for async function getOptions is finished before making asserts
    expect(await screen.findAllByRole("selectionTicker")).toHaveLength(3);
    expect(await screen.findAllByRole("selectionSunday")).toHaveLength(3);
});

/****************************************************************************************/

it('OptionSelectionRow Test Render', async () => {
    render(<OptionSelectionRow setMessage={console.log} option={option} selections={[]} />);
    await waitFor(() => {
        expect(screen.queryByRole('optionSelectionTicker')?.textContent).to.include(option.ticker);
    });
});

it('OptionSelectionTable Test 0 Selection / Option', async () => {
    render(<OptionSelectionTable setMessage={console.log} options={[]} selections={[]} optionsDispatch={console.log} selectionsDispatch={console.log} week={0}/>);
    await waitFor(() => {
        expect(screen.queryByRole('noSelections')?.textContent).to.include("selections");
    });
});

it('OptionSelectionTable Test 1 Selection / Option', async () => {
    render(<OptionSelectionTable setMessage={console.log} options={[option]} selections={[selection]} optionsDispatch={console.log} selectionsDispatch={console.log} week={0}/>);
    await waitFor(() => {
        expect(screen.queryByRole('optionSelectionTicker')?.textContent).to.include(option.ticker);
        //expect(screen.queryByRole('selectionSunday')?.textContent).to.include(selection.sunday);
        //expect(screen.queryByRole('sunday')?.textContent).to.include(option.sunday);
    });
});

it('OptionSelectionTable Test 3 Selections', async () => {
    render(<OptionSelectionTable setMessage={console.log} options={[option, option, option]} selections={[selection]} optionsDispatch={console.log} selectionsDispatch={console.log} week={0}/>);
    // must wait for async function getOptions is finished before making asserts
    expect(await screen.findAllByRole("optionSelectionTicker")).toHaveLength(3);
    expect(await screen.findAllByRole("optionSelectionSunday")).toHaveLength(3);
});