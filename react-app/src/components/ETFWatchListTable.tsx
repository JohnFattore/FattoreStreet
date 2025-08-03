import Table from "react-bootstrap/Table";
import { formatString } from "../functions/helperFunctions";
import { IETFInfo } from "../interfaces";
import { useGetAssetInfosQuery } from "../functions/api";
import { Button, Spinner } from "react-bootstrap";
import { useSelector, useDispatch } from "react-redux";
import { AppDispatch, RootState } from "../main";
import { removeTicker } from "../reducers/watchListReducer";
import { useNavigate } from "react-router-dom";

function ETFWatchListRow({ etfInfo }) {
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();
  return (
    <tr>
      <td>{formatString(etfInfo.ticker, "text")}</td>
      <td>{formatString(etfInfo.shortName, "text")}</td>
      <td>{formatString(etfInfo.currentPrice, "money")}</td>
      <td>{formatString(etfInfo.percentChangeDaily, "percent")}</td>
      <td>{formatString(etfInfo.marketCap, "money")}</td>
      <td>{formatString(etfInfo.expenseRatio, "percent")}</td>
      <td>
        <Button
          onClick={() => dispatch(removeTicker(etfInfo.ticker))}
        >{`Remove ${etfInfo.ticker}`}</Button>
      </td>
      <td>
        <Button
          onClick={() => {
            navigate(`/asset/${etfInfo.ticker}`);
          }}
        >{`View ${etfInfo.ticker}`}</Button>
      </td>
    </tr>
  );
}

export default function ETFWatchListTable() {
  const tickers = useSelector((state: RootState) => state.watchList.tickers);
  const { data } = useGetAssetInfosQuery(tickers, {
    skip: tickers.length === 0,
  });
  if (!data) return <Spinner animation="border" />;

  const etfInfos = Object.values(data).filter(
    (item): item is IETFInfo => item.type === "ETF"
  );
  if (etfInfos.length == 0) {
    return <h3 role="noModels">No Data</h3>;
  }

  return (
    <Table>
      <thead>
        <tr>
          <th>Ticker</th>
          <th>Name</th>
          <th>Price</th>
          <th>Percent Change Today</th>
          <th>AUM</th>
          <th>Expense Ratio</th>
          <th>Remove</th>
          <th>View Asset</th>
        </tr>
      </thead>
      <tbody>
        {etfInfos.map((etfInfo: IETFInfo) => (
          <ETFWatchListRow key={etfInfo.ticker} etfInfo={etfInfo} />
        ))}
      </tbody>
    </Table>
  );
}
