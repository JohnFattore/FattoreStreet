import { formatString, getErrorMessages } from "../functions/helperFunctions";
import { IEquityInfo, IETFInfo } from "../interfaces";
import { useGetAssetInfosQuery } from "../functions/api";
import { Button, Spinner, Table, Alert } from "react-bootstrap";
import { useSelector, useDispatch } from "react-redux";
import { AppDispatch, RootState } from "../main";
import { removeTicker } from "../reducers/watchListReducer";
import { useNavigate } from "react-router-dom";

function WatchListRow({ assetInfo }) {
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();
  return (
    <tr>
      <td>{formatString(assetInfo.ticker, "text")}</td>
      <td>{formatString(assetInfo.shortName, "text")}</td>
      <td>{assetInfo.type}</td>
      <td>{formatString(assetInfo.currentPrice, "money")}</td>
      <td>{formatString(assetInfo.percentChangeDaily, "percent")}</td>
      <td>{formatString(assetInfo.percentChangeWeekly, "percent")}</td>
      <td>{formatString(assetInfo.percentChangeMonthly, "percent")}</td>
      <td>{formatString(assetInfo.percentChangeYTD, "percent")}</td>
      <td>{formatString(assetInfo.percentChangeYearly, "percent")}</td>
      <td>{formatString(assetInfo.percentChange3Years, "percent")}</td>
      <td>{formatString(assetInfo.percentChange5Years, "percent")}</td>
      <td>
        <Button
          onClick={() => dispatch(removeTicker(assetInfo.ticker))}
        >{`Remove ${assetInfo.ticker}`}</Button>
      </td>
      <td>
        <Button
          onClick={() => {
            navigate(`/asset/${assetInfo.ticker}`);
          }}
        >{`View ${assetInfo.ticker}`}</Button>
      </td>
    </tr>
  );
}

export default function WatchListTable() {
  const tickers = useSelector((state: RootState) => state.watchList.tickers);
  const { data, error } = useGetAssetInfosQuery(tickers, {
    skip: tickers.length === 0,
  });
  if (error)
    return <Alert variant="danger">{getErrorMessages(error["data"])}</Alert>;

  if (!data) return <Spinner animation="border" />;

  return (
    <Table>
      <thead>
        <tr>
          <th>Ticker</th>
          <th>Name</th>
          <th>Type</th>
          <th>Price</th>
          <th>Percent Change Today</th>
          <th>Percent Change Weekly</th>
          <th>Percent Change Monthly</th>
          <th>Percent Change YTD</th>
          <th>Percent Change 1 Year</th>
          <th>Percent Change 3 Years</th>
          <th>Percent Change 5 Years</th>
          <th>Remove</th>
          <th>View Asset</th>
        </tr>
      </thead>
      <tbody>
        {Object.values(data as Record<string, IEquityInfo | IETFInfo>).map(
          (assetInfo) => (
            <WatchListRow key={assetInfo.ticker} assetInfo={assetInfo} />
          )
        )}
      </tbody>
    </Table>
  );
}
