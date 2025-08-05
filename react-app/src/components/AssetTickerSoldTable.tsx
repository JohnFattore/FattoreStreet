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

function AssetRow({ asset, assetInfo }) {
  const [showSell, setShowSell] = useState(false);
  const handleCloseSell = () => setShowSell(false);
  const handleShowSell = () => setShowSell(true);

  const [showDelete, setShowDelete] = useState(false);
  const handleCloseDelete = () => setShowDelete(false);
  const handleShowDelete = () => setShowDelete(true);
  const [deleteAsset, { error, isLoading }] = useDeleteAssetMutation();

  if (!assetInfo) return null;
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
  const { data: assetInfos, isLoading } = useGetAssetInfosQuery([ticker]);
  const { access } = useSelector((state: RootState) => state.user);

  if (!access) {
    return <></>;
  }

  if (!assets) return <Spinner animation="border" />;

  const assetsSold = assets.filter((item) => item.sellDate);

  if (assetsSold.length == 0 && access && !isLoading) {
    return null;
  }

  if (access && isLoading) return <Spinner animation="border" />;

  return (
    <>
      <h3>Purchased Assets</h3>
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
