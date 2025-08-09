import Table from "react-bootstrap/Table";
import { Button, Spinner, Modal, Alert } from "react-bootstrap";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import {
  useGetAssetsQuery,
  useGetAssetInfosQuery,
  useDeleteAssetMutation,
} from "../functions/api";
import { formatString, getErrorMessages } from "../functions/helperFunctions";
import { useState } from "react";
import AssetSellForm from "../components/AssetSellForm";

function AssetRow({ asset }) {
  const [showSell, setShowSell] = useState(false);
  const handleCloseSell = () => setShowSell(false);
  const handleShowSell = () => setShowSell(true);

  const [showDelete, setShowDelete] = useState(false);
  const handleCloseDelete = () => setShowDelete(false);
  const handleShowDelete = () => setShowDelete(true);
  const [deleteAsset, { error, isLoading }] = useDeleteAssetMutation();

  return (
    <>
      <Modal show={showSell} onHide={handleCloseSell}>
        <Modal.Header closeButton>
          <Modal.Title>{`Sell ${asset?.ticker}`}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {}
          {`Would you like to sell ${asset?.ticker}?`}
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={handleCloseSell}>
            Close
          </Button>
          <AssetSellForm asset={asset} />
        </Modal.Footer>
      </Modal>

      <Modal show={showDelete} onHide={handleCloseDelete}>
        <Modal.Header closeButton>
          <Modal.Title>{`Delete ${asset?.ticker}`}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {error ? (
            <Alert variant="danger">{getErrorMessages(error["data"])}</Alert>
          ) : null}
          {`Would you like to delete ${asset?.ticker}?`}
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={handleCloseDelete}>
            Close
          </Button>
          <Button
            disabled={isLoading}
            onClick={async () => {
              await deleteAsset(asset?.id!);
            }}
          >{`Delete ${asset?.ticker}`}</Button>
        </Modal.Footer>
      </Modal>
      <tr>
        <td>{asset.ticker}</td>
        <td>{formatString(asset.shares, "amount")}</td>
        <td>{asset.buyDate}</td>
        <td>{formatString(asset.buyPrice, "money")}</td>
        <td>{asset.sellDate}</td>
        <td>{formatString(asset.sellPrice, "money")}</td>
        <td>
          {formatString(
            (asset.sellPrice - asset.buyPrice) / asset.buyPrice,
            "percent"
          )}
        </td>
        <td>
          <Button onClick={handleShowSell}>{`Sell ${asset?.ticker}`}</Button>
        </td>
        <td>
          <Button
            onClick={handleShowDelete}
          >{`Delete ${asset?.ticker}`}</Button>
        </td>
      </tr>
    </>
  );
}

export default function AssetTickerSoldTable({ ticker }) {
  const { data: allAssets } = useGetAssetsQuery();
  const assets = allAssets?.filter((a) => a.ticker === ticker);
  const { data: assetInfosRaw, isLoading } = useGetAssetInfosQuery([ticker]);
  const { access } = useSelector((state: RootState) => state.user);
  const assetInfos = assetInfosRaw ?? {}

  if (!access) {
    return <></>;
  }

  if (!assets) return <Spinner animation="border" />;

  const assetsSold = assets.filter((item) => item.sellDate);

  if (assetsSold.length == 0 && access && !isLoading) {
    return null;
  }

  if (access && isLoading) return <Spinner animation="border" />;

  const data = assetsSold.map((asset) => {
    const info = assetInfos[asset.ticker]; // I think you meant asset.ticker, not ticker
    if (!asset.sellPrice) {
      throw Error(`ticker ${asset.ticker} as no sell price`)
    }
    return {
      id: asset.id,
      ticker: asset.ticker,
      shares: asset.shares,
      buyDate: asset.buyDate,
      buyPrice: asset.buyPrice,
      sellDate: asset.sellDate,
      sellPrice: asset.sellPrice,
      percentChange: (asset.sellPrice - asset.buyPrice) / asset.buyPrice,
      shortName: info?.shortName ?? "Error Loading Info",
      hasError: !info,
    };
  });

  return (
    <>
      <h3>Sold Assets</h3>
      <Table>
        <thead>
          <tr>
            <th>Ticker</th>
            <th>Shares</th>
            <th>Buy Date</th>
            <th>Buy Price</th>
            <th>Sell Date</th>
            <th>Sell Price</th>
            <th>Percent Change</th>
            <th>Sell Asset</th>
            <th>Delete Asset</th>
          </tr>
        </thead>
        <tbody>
          {data.map((asset) => (
            <AssetRow
              key={asset.id}
              asset={asset}
            />
          ))}
        </tbody>
      </Table>
    </>
  );
}
