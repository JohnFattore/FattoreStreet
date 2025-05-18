import { useSelector } from "react-redux";
import { RootState } from '../main';
import { useNavigate } from "react-router-dom";
import { Button } from "react-bootstrap";
import { useEffect } from "react";
import { deleteAsset } from "../components/axiosFunctions";
import { useDispatch } from "react-redux";
import { AppDispatch } from '../main';
import Card from 'react-bootstrap/Card';
import Modal from 'react-bootstrap/Modal';
import { ListGroup, Alert } from "react-bootstrap";
import { useState } from "react";
import { formatString } from '../components/helperFunctions';
import AssetSellForm from "../components/AssetSellForm";

export default function AssetView() {
    const navigate = useNavigate();
    const dispatch = useDispatch<AppDispatch>();
    const { asset, loading, error } = useSelector((state: RootState) => state.assets);

    // remember, useEffect is used to properly execute side effects in react
    useEffect(() => {
        if (!asset) {
            navigate("/portfolio");
        }
    }, [asset, navigate]);

    if (!asset) return null;

    const [show, setShowSell] = useState(false);
    const handleCloseSell = () => setShowSell(false);
    const handleShowSell = () => setShowSell(true);

    const [showDelete, setShowDelete] = useState(false);
    const handleCloseDelete = () => setShowDelete(false);
    const handleShowDelete = () => setShowDelete(true);

    return (
        <>
            <Card>
                <Card.Body>
                    <Card.Title>{asset.long_name}</Card.Title>
                    <Card.Text>
                        {`${asset.ticker} trades on the ${asset.exchange}`}
                    </Card.Text>
                </Card.Body>
            </Card>

            <ListGroup>
                <ListGroup.Item>{`Current current price is ${formatString(asset.currentPrice, "money")}`}</ListGroup.Item>
                <ListGroup.Item>{`${asset.ticker} bought ${formatString(asset.shares, "amount")} shares on ${asset.buyDate} for $${asset.costBasis}`}</ListGroup.Item>
                <ListGroup.Item>{`The percent change is ${formatString(asset.percentChange, "percent")}, compared to the S&P 500 which changed ${formatString(asset.snp500PercentChange, "percent")} in the same time`}</ListGroup.Item>
                {asset.sellPrice ?  (<ListGroup.Item>{`${asset.ticker} sold for ${formatString(asset.sellPrice, "money")} on ${asset.sellDate}`}</ListGroup.Item>) : null}
            </ListGroup>

            <Button onClick={handleShowSell}>
                {`Sell ${asset.ticker}`}
            </Button>

            <Modal show={show} onHide={handleCloseSell}>
                <Modal.Header closeButton>
                    <Modal.Title>{`Sell ${asset.ticker}`}</Modal.Title>
                </Modal.Header>
                <Modal.Body>{`Would you like to sell ${asset.ticker}?`}
                    {error && <Alert variant="danger">{error}</Alert>}
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="secondary" onClick={handleCloseSell}>
                        Close
                    </Button>
                    <AssetSellForm/>
                </Modal.Footer>
            </Modal>

            <Button onClick={handleShowDelete}>
                {`Delete ${asset.ticker}`}
            </Button>

            <Modal show={showDelete} onHide={handleCloseDelete}>
                <Modal.Header closeButton>
                    <Modal.Title>{`Delete ${asset.ticker}`}</Modal.Title>
                </Modal.Header>
                <Modal.Body>{`Would you like to delete ${asset.ticker}?`}</Modal.Body>
                <Modal.Footer>
                    <Button variant="secondary" onClick={handleCloseDelete}>
                        Close
                    </Button>
                    <Button disabled={loading} onClick={() => {
                        dispatch(deleteAsset(asset.id))
                        navigate("/portfolio")
                    }}>{`Delete ${asset.ticker}`}</Button>
                </Modal.Footer>
            </Modal>

            <Button onClick={() => navigate("/portfolio")}>Back to Portfolio</Button>
        </>
    );
}