// @vitest-environment jsdom

import { render, screen } from '@testing-library/react';
import { expect, test, vi } from 'vitest'


import React from 'react';
import About from '../src/components/About'
import WatchList from '../src/pages/WatchList'
import WatchListForm from '../src/components/WatchListForm'
import WatchListClear from '../src/components/WatchListClear'
import WatchListRow from '../src/components/WatchListRow'

// Portfolio
import Portfolio from '../src/pages/Portfolio'
import AssetForm from '../src/components/AssetForm'
import AssetTable from '../src/components/AssetTable'
import AssetRow from '../src/components/AssetRow'
import AssetFormHeader from '../src/components/AssetFormHeader'
// Allocation
import Allocation from "../src/pages/Allocation";
import AllocationRow from "../src/components/AllocationRow";
import AllocationTable from "../src/components/AllocationTable";

import { ENVContext } from '../src/components/ENVContext';

test('renders Watchlist Components', () => {

  const ENV = {
    finnhubURL: 'mockedFinnhubURL',
    djangoURL: 'mockedDjangoURL',
    finnhubKey: 'mockedVITEST'
  };
  render(<WatchListForm />);
  render(<WatchListClear />);
  render(<WatchListRow />);
  render(
    <ENVContext.Provider value={ENV}>
      <WatchListForm />
      <WatchListClear />
      <WatchListRow />
      <WatchList />
    </ENVContext.Provider>
  )
});

test('renders Portfolio Components', () => {
  render(<Portfolio />);
  render(<AssetForm />);
  render(<AssetTable />);
  render(<AssetRow />);
  render(<AssetFormHeader />);
});

test('renders Allocation Components', () => {
  render(<Allocation />);
  render(<AllocationRow />);
  render(<AllocationTable />);
});