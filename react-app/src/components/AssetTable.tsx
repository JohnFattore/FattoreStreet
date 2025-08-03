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
      <td>{formatString(asset.shares, "amount")}</td>
      <td>{asset.buyDate}</td>
      <td>{formatString(asset.buyPrice, "money")}</td>
      <td>{formatString(assetInfo.currentPrice, "money")}</td>
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
            <th>Shares</th>
            <th>Buy Date</th>
            <th>Buy Price</th>
            <th>Current Price</th>
            <th>View Asset</th>
          </tr>
        </thead>
        <tbody>
          {assetsOwned.map((asset) => (
            <AssetRow
              key={asset.id}
              asset={asset}
              assetInfo={assetInfos?.[asset.ticker]}
            />
          ))}
        </tbody>
      </Table>
    </>
  );
}
