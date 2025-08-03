import Table from "react-bootstrap/Table";
import { formatString } from "../functions/helperFunctions";
import { IEquityInfo } from "../interfaces";
import { useGetAssetInfosQuery } from "../functions/api";
import { Button, Spinner } from "react-bootstrap";
import { useSelector, useDispatch } from "react-redux";
import { AppDispatch, RootState } from "../main";
import { removeTicker } from "../reducers/watchListReducer";
import { useNavigate } from "react-router-dom";

function EquityWatchListRow({ equityInfo }) {
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();
  return (
    <tr>
      <td>{formatString(equityInfo.ticker, "text")}</td>
      <td>{formatString(equityInfo.shortName, "text")}</td>
      <td>{formatString(equityInfo.currentPrice, "money")}</td>
      <td>{formatString(equityInfo.percentChangeDaily, "percent")}</td>
      <td>{formatString(equityInfo.marketCap, "money")}</td>
      <td>{formatString(equityInfo.trailingPE, "amount")}</td>
      <td>{formatString(equityInfo.incomeTTM, "money")}</td>
      <td>{formatString(equityInfo.revenueTTM, "money")}</td>
      <td>{formatString(equityInfo.netMarginTTM, "percent")}</td>
      <td>
        <Button
          onClick={() => dispatch(removeTicker(equityInfo.ticker))}
        >{`Remove ${equityInfo.ticker}`}</Button>
      </td>
      <td><Button onClick={() => {
              navigate(`/asset/${equityInfo.ticker}`);
            }}>{`view ${equityInfo.ticker}`}</Button></td>
    </tr>
  );
}

export default function EquityWatchListTable() {
  const tickers = useSelector((state: RootState) => state.watchList.tickers);
  const { data } = useGetAssetInfosQuery(tickers, {
    skip: tickers.length === 0,
  });
  if (!data) return <Spinner animation="border" />;

  const equityInfos = Object.values(data).filter(
    (item): item is IEquityInfo => item.type === "EQUITY"
  );
  if (equityInfos.length == 0) {
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
          <th>Market Cap</th>
          <th>PE Ratio</th>
          <th>Income TTM</th>
          <th>Revenue TTM</th>
          <th>Net Margins TTM</th>
          <th>Remove</th>
          <th>View Asset</th>
        </tr>
      </thead>
      <tbody>
        {equityInfos.map((equityInfo: IEquityInfo) => (
          <EquityWatchListRow key={equityInfo.ticker} equityInfo={equityInfo} />
        ))}
      </tbody>
    </Table>
  );
}
