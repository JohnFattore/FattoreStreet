import React from 'react';
import Table from 'react-bootstrap/Table';
import WatchListRow from './WatchListRow';

function WatchListTable() {
  //const [strDBTickers, setDBTickers] = React.useState("");
  //const [listTickers, setTickers] = React.useState<string[]>([]);
/*
  if (localStorage.getItem("listTickers") == null) {
    setTickers(["VTI", "SPY"]);
    setDBTickers(JSON.stringify(listTickers));
    localStorage.setItem("listTickers", strDBTickers);
  };
  setDBTickers(localStorage.getItem("listTickers") as string);
  setTickers(JSON.parse(strDBTickers) as string[]);
*/

// if no watch list yet, add a starter one
  if (localStorage.getItem("listTickers") == null) {
    let listTickers = (["VTI", "SPY"]);
    let strDBTickers = (JSON.stringify(listTickers));
    localStorage.setItem("listTickers", strDBTickers);
  };
  var strDBTickers = (localStorage.getItem("listTickers") as string);
  var listTickers = (JSON.parse(strDBTickers) as string[]);

  // if storage changes, update lists of tickers
  React.useEffect(() => {
    window.addEventListener('storage', () => {
      strDBTickers = (localStorage.getItem("listTickers") as string);
      listTickers = (JSON.parse(strDBTickers) as string[]);
    });
  }, [])
  

  return (
    <Table>
      <thead>
        <tr>
          <th scope="col">Ticker</th>
          <th scope="col">Current Price</th>
          <th scope="col">Daily Percent Change</th>
        </tr>
      </thead>
      <tbody>
      {listTickers.map(strTicker => (
          <WatchListRow strTicker={strTicker} />
        ))}
      </tbody>
    </Table>
  );
}

export default WatchListTable;