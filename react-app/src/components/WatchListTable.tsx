import Table from 'react-bootstrap/Table';
import WatchListRow from './WatchListRow';

export default function WatchListTable({setMessage, tickers, setTickers}) {
 
  return (
    <Table>
      <thead>
        <tr>
          <th scope="col">Ticker</th>
          <th scope="col">Current Price</th>
          <th scope="col">Daily Percent Change</th>
          <th scope="col">Market Cap</th>
          <th scope="col">Net Income</th>
          <th>Delete</th>
        </tr>
      </thead>
      <tbody>
        {tickers.map((ticker: string, index: number) => (
          <WatchListRow ticker={ticker} setMessage={setMessage} setTickers={setTickers} key={index}/>
        ))}
      </tbody>
    </Table>
  );
}