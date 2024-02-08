// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { expect, test, vi, afterEach } from 'vitest'
import React from 'react';
import WatchList from '../src/pages/WatchList'
import WatchListForm from '../src/components/WatchListForm'
import WatchListClear from '../src/components/WatchListClear'
import WatchListRow from '../src/components/WatchListRow'
import WatchListTable from '../src/components/WatchListTable';
import { ENVContext } from '../src/components/ENVContext';

afterEach(() => { document.body.innerHTML = ''; });
const ENV = {
  finnhubURL: "https://finnhub.io/api/v1/",
  djangoURL: "http://127.0.0.1:8000/portfolio/api/",
  finnhubKey: "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"
};

test('WatchList Page Test', () => {
  render(
    <ENVContext.Provider value={ENV}>
      <WatchList />
    </ENVContext.Provider>
  );
});

test('WatchList Form Test', () => {
  render(
    <ENVContext.Provider value={ENV}>
      <WatchListForm setChange={console.log}/>
    </ENVContext.Provider>
  );
});

test('WatchListTable Test', () => {
  render(
    <ENVContext.Provider value={ENV}>
      <WatchListTable change={false} setChange={console.log}/>
    </ENVContext.Provider>
  );
});

test('WatchList Row Test', () => {
  render(
    <ENVContext.Provider value={ENV}>
      <WatchListRow ticker={"SPY"} setChange={console.log}/>
    </ENVContext.Provider>
  );
});

test('WatchList Clear Test', () => {
  render(
    <ENVContext.Provider value={ENV}>
      <WatchListClear setChange={console.log}/>
    </ENVContext.Provider>
  );
});