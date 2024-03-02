// @vitest-environment jsdom
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { expect, test, vi, afterEach } from 'vitest'
import React from 'react';
import WatchListForm from '../src/components/WatchListForm'
import WatchListRow from '../src/components/WatchListRow'
import WatchListTable from '../src/components/WatchListTable';
import { IMessage } from '../src/interfaces';

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  localStorage.clear();
});

const message: IMessage = { text: "", type: "" }

vi.mock('../src/components/AxiosFunctions', () => ({
  __esModule: true,
  getQuote: vi.fn(() => new Promise((resolve) => resolve({ data: { c: 200, d: 3, dp: 1.5 } }))),
}));

// delete button is pushed in integration test
test('WatchList Row Test', () => {
  render(<WatchListRow ticker={"SPY"} setMessage={console.log} setTickers={console.log} index={1}/>);
  expect(screen.queryByRole("ticker")?.textContent).to.include("SPY")
  expect(screen.queryByRole("percentChange")?.textContent).toBeDefined
  expect(screen.queryByRole("delete")?.textContent).to.equal("delete")
});

// adding a ticker is tested in integration test
test('WatchListTable Test', () => {
  render(<WatchListTable setMessage={console.log} tickers={[]} setTickers={console.log}/>);
});

// adding a ticker is tested in integration test
test('WatchListTable Test', () => {
  render(<WatchListTable setMessage={console.log} tickers={["VTI", "SPY"]} setTickers={console.log}/>);
  expect(screen.queryAllByRole("ticker")).toHaveLength(2);
  expect(screen.queryAllByRole("percentChange")).toHaveLength(2);
  expect(screen.queryAllByRole("delete")).toHaveLength(2);
});

test('WatchList Form Test blank submit', async () => {
  render(<WatchListForm setMessage={console.log} setTickers={console.log}/>);
  fireEvent.submit(screen.getByRole("button"));

    await waitFor(async () => {
      expect(screen.queryByRole("tickerError")?.textContent).toBeDefined();
    });
});

test('WatchList Form Test successful submit', async () => {
  render(<WatchListForm setMessage={console.log} setTickers={console.log}/>);
  fireEvent.input(screen.getByPlaceholderText("Enter Ticker Here"), { target: { value: "VTI", }, });
  fireEvent.submit(screen.getByRole("button"));

    await waitFor(async () => {
      expect(screen.queryByRole("tickerError")?.textContent).not.toBeDefined();
    });
});