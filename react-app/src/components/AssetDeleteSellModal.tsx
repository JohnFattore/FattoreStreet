import { Button, Modal, Alert } from "react-bootstrap";
import { useDeleteAssetMutation } from "../functions/api";
import AssetSellForm from "./AssetSellForm";
import { getApiErrorMessages } from "../functions/helperFunctions";
import { IAsset } from "../interfaces";

interface Props {
  asset: IAsset | undefined;
  showSell: boolean;
  showDelete: boolean;
  handleCloseSell: () => void;
  handleCloseDelete: () => void;
}

export default function AssetDeleteSellModal({asset, showSell, showDelete, handleCloseSell, handleCloseDelete}: Props) {

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
            <Alert variant="danger">{getApiErrorMessages(error)}</Alert>
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
    </>
  );
}
