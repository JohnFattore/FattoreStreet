// @vitest-environment jsdom
import { render, screen, cleanup } from '@testing-library/react';
import { expect, test, vi, afterEach } from 'vitest'
import React from 'react';
import WatchListForm from '../src/components/WatchListForm'
import WatchListClear from '../src/components/WatchListClear'
import WatchListRow from '../src/components/WatchListRow'
import WatchListTable from '../src/components/WatchListTable';

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

vi.mock('../src/components/AxiosFunctions', () => ({
  __esModule: true,
  getAssets: vi.fn(() => new Promise((resolve) => resolve({ data: [{ ticker: "VTI", shares: 5, costbasis: 180, buy: '2023-02-14' }] }))),
  getQuote: vi.fn(() => new Promise((resolve) => resolve({ data: { c: 200, d: 3, dp: 1.5 } }))),
}));

test('WatchList Form Test', () => {
  render(<WatchListForm setChange={console.log}/>);
});

test('WatchListTable Test', () => {
  render(<WatchListTable change={false} setChange={console.log}/>);
});

test('WatchList Row Test', () => {
  render(<WatchListRow ticker={"SPY"} setChange={console.log}/>);
});

test('WatchList Clear Test', () => {
  render(<WatchListClear setChange={console.log}/>);
});