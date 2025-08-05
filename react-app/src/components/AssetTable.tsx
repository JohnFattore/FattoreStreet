import Table from "react-bootstrap/Table";
import { Button, Spinner } from "react-bootstrap";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { useGetAssetsQuery, useGetAssetInfosQuery } from "../functions/api";
import { formatString } from "../functions/helperFunctions";
import { useNavigate } from "react-router-dom";
import { useEffect } from "react";

function AssetRow({ asset, assetInfo }) {
  const navigate = useNavigate();
  if (!asset || !assetInfo) return null;
  return (
    <tr>
      <td>{asset.ticker}</td>
      <td>{assetInfo.shortName}</td>
      <td>{formatString(asset.totalShares, "amount")}</td>
      <td>{formatString(asset.averageBuyPrice, "money")}</td>
      <td>{formatString(asset.totalCost, "money")}</td>
      <td>
        {formatString(assetInfo.currentPrice * asset.totalShares, "money")}
      </td>
      <td>
        {formatString(
          ((assetInfo.currentPrice * asset.totalShares) - asset.totalCost) /
            asset.totalCost,
          "percent"
        )}
      </td>
      <td>
        <Button
          onClick={() => {
            navigate(`/asset/${asset.ticker}`);
          }}
        >{`View ${asset.ticker}`}</Button>
      </td>
    </tr>
  );
}

export default function AssetTable() {
  const { access } = useSelector((state: RootState) => state.user);
  const { data: assets, refetch } = useGetAssetsQuery();
  const tickers = [...new Set(assets?.map((asset) => asset.ticker) ?? [])];
  const { data: assetInfos, isLoading } = useGetAssetInfosQuery(tickers, {
    skip: tickers.length === 0 || !access,
  });
  useEffect(() => {
    if (access) {
      refetch();
    }
  }, [access, refetch]);

  if (!access) {
    return <></>;
  }

  if (!assets)
    return (
      <>
        <h3>Assets Owned</h3>
        <Spinner animation="border" />
      </>
    );
  const assetsOwned = assets.filter((item) => !item.sellDate);

  if (assetsOwned.length == 0 && access && !isLoading) {
    return null;
  }

  const combinedAssets: Record<
    string,
    { totalShares: number; totalCost: number }
  > = {};
  for (const asset of assetsOwned) {
    if (!combinedAssets[asset.ticker]) {
      combinedAssets[asset.ticker] = { totalShares: 0, totalCost: 0 };
    }

    combinedAssets[asset.ticker].totalShares += asset.shares;
    combinedAssets[asset.ticker].totalCost += asset.buyPrice;
  }

  const combined = Object.entries(combinedAssets).map(([ticker, data]) => ({
    ticker,
    totalShares: data.totalShares,
    totalCost: data.totalCost,
    averageBuyPrice: data.totalCost / data.totalShares,
  }));

  if (isLoading)
    return (
      <>
        <h3>Assets Owned</h3>
        <Spinner animation="border" />
      </>
    );

  return (
    <>
      <h3>Assets Owned</h3>
      <Table>
        <thead>
          <tr>
            <th>Ticker</th>
            <th>Name</th>
            <th>Total Shares</th>
            <th>Average Buy Price</th>
            <th>Total Buy Price</th>
            <th>Current Price</th>
            <th>Percent Change</th>
            <th>View Asset</th>
          </tr>
        </thead>
        <tbody>
          {combined.map((asset) => (
            <AssetRow
              key={asset.ticker}
              asset={asset}
              assetInfo={assetInfos?.[asset.ticker]}
            />
          ))}
        </tbody>
      </Table>
    </>
  );
}
