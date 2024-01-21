import React from 'react';
import Table from 'react-bootstrap/Table';
import WatchListRow from './WatchListRow';

function WatchListTable({change, setChange}) {
  const [tickers, setTickers] = React.useState<String[]>([]);
  // if storage changes, update lists of tickers, doesnt work
  React.useEffect(() => {
    
    // if no watch list yet, add a starter one
    if (localStorage.getItem("tickers") == null) {
      let tickers: string[] = (["VTI", "SPY"]);
      let tickersDB: string = (JSON.stringify(tickers));
      localStorage.setItem("tickers", tickersDB);
    };
    
    // tickers stored as string, but handled as array of strings
    var tickersDB = (localStorage.getItem("tickers") as string);
    setTickers(JSON.parse(tickersDB) as string[]);
    setChange(false)
  }, [change])

  return (
    <Table>
      <thead>
        <tr>
          <th scope="col">Ticker</th>
          <th scope="col">Current Price</th>
          <th scope="col">Daily Percent Change</th>
          <th>Delete</th>
        </tr>
      </thead>
      <tbody>
        {tickers.map(ticker => (
          <WatchListRow ticker={ticker} setChange={setChange}/>
        ))}
      </tbody>
    </Table>
  );
}

export default WatchListTable;