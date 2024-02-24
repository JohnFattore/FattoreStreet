import { useEffect } from 'react';
import Table from 'react-bootstrap/Table';
import WatchListRow from './WatchListRow';

export default function WatchListTable({setMessage, tickers, setTickers}) {
  //const [tickers, setTickers] = useState<String[]>([]);
  // if storage changes, update lists of tickers, doesnt work
  useEffect(() => {
    /*
    // if no watch list yet, add a starter one
    if (localStorage.getItem("tickers") == null) {
      let allTickers: string[] = (["VTI", "SPY"]);
      let tickersDB: string = (JSON.stringify(allTickers));
      localStorage.setItem("tickers", tickersDB);
    };
    */
  
    // tickers stored as string, but handled as array of strings
    var tickersDB = (localStorage.getItem("tickers") as string);
    setTickers(JSON.parse(tickersDB) as string[]);
  }, [])

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
        {tickers.map((ticker: string, index: number) => (
          <WatchListRow ticker={ticker} setMessage={setMessage} setTickers={setTickers} index={index}/>
        ))}
      </tbody>
    </Table>
  );
}