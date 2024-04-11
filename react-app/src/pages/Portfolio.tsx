import AssetTable from '../components/AssetTable';
import AssetForm from '../components/AssetForm';
import Alert from 'react-bootstrap/Alert';
import { useState } from 'react';
import { IMessage } from '../interfaces';
import { setAlertVarient } from '../components/helperFunctions';
import { useReducer } from 'react';
import DjangoTable from '../components/DjangoTable';
import { useQuote } from '../components/customHooks';


function assetReducer(assets, action) {
    switch (action.type) {
        case 'add': {
            return [...assets, action.asset];
        }
        case 'delete': {
            return assets.filter(e => e !== action.asset)
        }
    }
}

function multipy(num1, num2) {
    return num1 * num2
}

export default function Portfolio() {
    const [message, setMessage] = useState<IMessage>({ text: "", type: "" })
    const [assets, dispatch] = useReducer(assetReducer, []);

    const fields = {
        ticker: {name: "Ticker" },
        shares: {name: "Shares", type: "amount" },
        costbasis: {name: "Cost Basis", type: "money"},
        buy: {name: "Buy Date" },
        totalCostBasis: {name: "Total Cost Basis", function: multipy, parameters: ['shares', 'costbasis'], type: "money" },
        quote: {name: "Quote", function: useQuote, parameters: ['ticker', setMessage], item: "price", type: "money" },
        marketPrice: {name: "Market Price", function: multipy, parameters: ['shares', 'totalCostBasis'], type: "money" },
    }

    return (
        <>
            <h3>Add Assets</h3>
            <AssetForm setMessage={setMessage} dispatch={dispatch} />
            <h1 role="assetTableHeader">User's Portfolio</h1>
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
            <AssetTable setMessage={setMessage} assets={assets} dispatch={dispatch} />
            <DjangoTable setMessage={setMessage} models={assets} dispatch={dispatch} fields={fields} />
        </>
    );
}