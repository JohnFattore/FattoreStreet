import Table from "react-bootstrap/Table";
import { Alert } from "react-bootstrap";
import { useWatchListData } from "./customHooks";
import { formatString } from "./helperFunctions";
import { useSelector, useDispatch } from "react-redux";
import { RootState, AppDispatch } from "../main";
import { removeTicker } from "../reducers/watchListReducer";
import { Button } from 'react-bootstrap';

const fields = [
  { name: "Ticker", type: "text", field: "ticker" },
  { name: "Name", type: "text", field: "name" },
  { name: "Price", type: "money", field: "price" },
  { name: "Percent Change", type: "percent", field: "percentChange" },
  { name: "Market Cap", type: "marketCap", field: "marketCap" },
  { name: "PE Ratio", type: "amount", field: "trailingPERatio" },
  { name: "Income TTM", type: "money", field: "incomeTTM" },
  { name: "Revenue TTM", type: "money", field: "revenueTTM" },
  { name: "Net Margins TTM", type: "percent", field: "netMarginTTM" },
  { name: "Delete", type: "delete", field: "delete" },
];

function WatchListRow({ ticker, fields }) {
  const dispatch = useDispatch<AppDispatch>();
  
  let watchListItem = useWatchListData(ticker);

  let tableData: JSX.Element[] = [];

  for (let i = 0; i < fields.length; i++) {
    if (fields[i]["name"] == "Delete") {
      tableData.push(
        <td key={i}> 
        <Button
          onClick={() => {
            dispatch(removeTicker(watchListItem.ticker));
                }}>
      {`Delete ${watchListItem.ticker}`}</Button> </td>);
    } 
    else {
      tableData.push(
        <td key={i}>
          {formatString(watchListItem[fields[i]["field"]], fields[i]["type"])}
        </td>
      );
    }
  }
  return <tr>{tableData}</tr>;
}

export default function WatchListTable() {
  const { tickers, loading } = useSelector(
    (state: RootState) => state.watchList
  );

  let headers: JSX.Element[] = [];
  for (let i = 0; i < fields.length; i++) {
    headers.push(
      <th key={i} /*onClick={() => handleSort(fields[i]["field"])}*/>
        {fields[i].name}
      </th>
    );
  }

  if (loading) return <Alert>Loading WatchList</Alert>;

  if (tickers.length == 0) {
    return <h3 role="noModels">No Data</h3>;
  }

  return (
    <Table>
      <thead>
        <tr>{headers}</tr>
      </thead>
      <tbody>
        {tickers.map((ticker) => (
          <WatchListRow key={ticker} ticker={ticker} fields={fields} />
        ))}
      </tbody>
    </Table>
  );
}
