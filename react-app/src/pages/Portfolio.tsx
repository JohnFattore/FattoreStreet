import AssetForm from '../components/AssetForm';
import Alert from 'react-bootstrap/Alert';
import { useState, useEffect } from 'react';
import { IMessage, IAsset } from '../interfaces';
import { setAlertVarient } from '../components/helperFunctions';
import { useReducer } from 'react';
import DjangoTable from '../components/DjangoTable';
import { useQuote } from '../components/customHooks';
import { deleteAsset, getAssets } from '../components/axiosFunctions';
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

function multipy(num1, num2) {
    return num1 * num2
}

function percentChange(start, end) {
    return (100 * (end - start) / start)
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

    const fields = {
        ticker: { name: "Ticker", type: "text" },
        shares: { name: "Shares", type: "amount" },
        costbasis: { name: "Cost Basis", type: "hidden" },
        quote: { name: "Quote", function: useQuote, parameters: ['ticker', setMessage], item: "price", type: "hidden" },
        totalCostBasis: { name: "Total Cost Basis", function: multipy, parameters: ['shares', 'costbasis'], type: "money" },
        marketPrice: { name: "Total Market Price", function: multipy, parameters: ['shares', 'quote'], type: "money" },
        percentChange: { name: "Percent Change", function: percentChange, parameters: ['totalCostBasis', 'marketPrice'], type: "percent" },
        SnP500Price: { name: "S&P 500 Price On Buy Date", type: "hidden" },
        SnP500Quote: { name: "SnP500 Quote", function: useQuote, parameters: ['SPY', setMessage], item: "price", type: "hidden" },
        SnP500PercentChange: { name: "S&P 500 % Change", function: percentChange, parameters: ['SnP500Price', 'SnP500Quote'], type: "percent" },
        buy: { name: "Buy Date", type: "text" },
        delete: { name: "Delete", function2: deleteAsset, type: "delete" }
    }

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
            <DjangoTable setMessage={setMessage} models={assets} dispatch={dispatch} fields={fields} />
        </>
    );
}