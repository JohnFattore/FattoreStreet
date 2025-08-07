import { Spinner, Button, Alert, Table } from "react-bootstrap";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { useGetAssetsQuery } from "../functions/api";
import { formatString, getErrorMessages } from "../functions/helperFunctions";
import { useGetAssetInfosQuery } from "../functions/api";
import { useNavigate } from "react-router-dom";
import { useEffect } from "react";

function AssetRow({ asset, assetInfo }) {
  const navigate = useNavigate();
  if (!assetInfo) return null;
  return (
    <tr>
      <td>{asset.ticker}</td>
      <td>{assetInfo.shortName}</td>
      <td>{formatString(asset.totalShares, "amount")}</td>
      <td>{formatString(asset.averageBuyPrice, "money")}</td>
      <td>{formatString(asset.totalCost, "money")}</td>
      <td>{formatString(asset.totalSalePrice, "money")}</td>
      <td>
        {formatString(
          (asset.totalSalePrice - asset.totalCost) / asset.totalCost,
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
  const { data: assets, refetch, error: assetError } = useGetAssetsQuery();
  const tickers = [...new Set(assets?.map((asset) => asset.ticker) ?? [])];
  const {
    data: assetInfos,
    isLoading,
    error: assetInfoError,
  } = useGetAssetInfosQuery(tickers, {
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
  if (assetError)
    return (
      <Alert variant="danger">{getErrorMessages(assetError["data"])}</Alert>
    );
  if (assetInfoError)
    return (
      <Alert variant="danger">{getErrorMessages(assetInfoError["data"])}</Alert>
    );
  if (!assets)
    return (
      <>
        <h3>Assets Sold</h3>
        <Spinner animation="border" />
      </>
    );

  const assetsSold = assets.filter((item) => item.sellDate);

  if (assetsSold.length == 0 && access && !isLoading) {
    return null;
  }
  const combinedAssets: Record<
    string,
    { totalShares: number; totalCost: number; totalSalePrice: number }
  > = {};
  for (const asset of assetsSold) {
    if (!combinedAssets[asset.ticker]) {
      combinedAssets[asset.ticker] = {
        totalShares: 0,
        totalCost: 0,
        totalSalePrice: 0,
      };
    }

    combinedAssets[asset.ticker].totalShares += asset.shares;
    combinedAssets[asset.ticker].totalCost += asset.buyPrice;
    combinedAssets[asset.ticker].totalSalePrice += asset.sellPrice!;
  }

  const combined = Object.entries(combinedAssets).map(([ticker, data]) => ({
    ticker,
    totalShares: data.totalShares,
    totalCost: data.totalCost,
    totalSalePrice: data.totalSalePrice,
    averageBuyPrice: data.totalCost / data.totalShares,
  }));

  if (isLoading)
    return (
      <>
        <h3>Assets Sold</h3>
        <Spinner animation="border" />
      </>
    );

  return (
    <>
      <h3>Assets Sold</h3>
      <Table>
        <thead>
          <tr>
            <th>Ticker</th>
            <th>Name</th>
            <th>Total Shares Sold</th>
            <th>Average Buy Price</th>
            <th>Total Cost</th>
            <th>Total Sale Price</th>
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
