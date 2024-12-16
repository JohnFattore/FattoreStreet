import AssetForm from '../components/AssetForm';
import Alert from 'react-bootstrap/Alert';
import { useState, useEffect } from 'react';
import { IMessage, IAsset } from '../interfaces';
import { setAlertVarient } from '../components/helperFunctions';
import { useReducer } from 'react';
import AssetTable from '../components/AssetTable';
import { getAssets } from '../components/axiosFunctions';
import { handleError } from '../components/helperFunctions';
import LoginForm from '../components/LoginForm';
import { Row, Col, Accordion } from 'react-bootstrap';

function assetReducer(assets, action) {
    switch (action.type) {
        case 'add': {
            return [...assets, action.asset];
        }
        case 'delete': {
            return assets.filter(e => e !== action.asset)
        }
        case 'refresh': {
            return [...assets]
        }
    }
}

export default function Portfolio() {
    const [message, setMessage] = useState<IMessage>({ text: "", type: "" })
    const [assets, dispatch] = useReducer(assetReducer, []);

    // this could totally be included in DjangoTable
    let data: IAsset[] = []
    useEffect(() => {
        if (assets.length == 0) {
            getAssets()
                .then((response) => {
                    for (let i = 0; i < response.data.length; i++) {
                        data.push({
                            ticker: response.data[i].ticker,
                            shares: response.data[i].shares,
                            costbasis: response.data[i].costbasis,
                            buy: response.data[i].buy,
                            SnP500Price: response.data[i].SnP500Price.price,
                            id: response.data[i].id
                        })
                    } for (let i = 0; i < data.length; i++) {
                        dispatch({ type: "add", asset: data[i] })
                    }
                }).catch((error) => {
                    handleError(error, setMessage);
                    //setMessage({ text: "There was a problem getting assets", type: "error" })
                })
        }
    }, []);

    return (
        <>
            <h3>Add Assets</h3>
            <Row>
                <Col>
                    <AssetForm setMessage={setMessage} dispatch={dispatch} />
                </Col>
                <Col>
                    <Accordion defaultActiveKey="1">
                        <Accordion.Item eventKey="0">
                            <Accordion.Header>Login</Accordion.Header>
                            <Accordion.Body>
                                <LoginForm setMessage={setMessage} />
                            </Accordion.Body>
                        </Accordion.Item>
                    </Accordion>
                </Col>
            </Row>
            <h1 role="assetTableHeader">User's Portfolio</h1>
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
            <AssetTable assets={assets} dispatch={dispatch} setMessage={setMessage} />
        </>
    );
}