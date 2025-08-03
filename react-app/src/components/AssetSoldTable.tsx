import Table from "react-bootstrap/Table";
import { Spinner, Button } from "react-bootstrap";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { useGetAssetsQuery } from "../functions/api";
import { formatString } from "../functions/helperFunctions";
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
      <td>{formatString(asset.shares, "amount")}</td>
      <td>{asset.buyDate}</td>
      <td>{formatString(asset.buyPrice, "money")}</td>
      <td>{asset.sellDate}</td>
      <td>{formatString(asset.sellPrice, "money")}</td>
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
    skip: tickers.length === 0 || !access
  });

  useEffect(() => {
    if (access) {
      refetch();
    }
  }, [access, refetch]);

  if (!access) {
    return <></>;
  }

  if (!assets) return <Spinner animation="border" />;
  const assetsSold = assets.filter((item) => item.sellDate);

  if (assetsSold.length == 0 && access && !isLoading) {
    return null
  }

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
            <th>Shares</th>
            <th>Buy Date</th>
            <th>Buy Price</th>
            <th>Sell Date</th>
            <th>Sell Price</th>
            <th>View Asset</th>
          </tr>
        </thead>
        <tbody>
          {assetsSold.map((asset) => (
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
