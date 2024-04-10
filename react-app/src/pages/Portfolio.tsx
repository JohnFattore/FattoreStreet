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

    const totalCostBasis =  {field: "totalCostBasis", function: multipy, parameters: "shares, costbasis" }
    const extraFields = [totalCostBasis] 
    const excludeFields = ["id", "user"]

    return (
        <>
            <h3>Add Assets</h3>
            <AssetForm setMessage={setMessage} dispatch={dispatch} />
            <h1 role="assetTableHeader">User's Portfolio</h1>
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
            <AssetTable setMessage={setMessage} assets={assets} dispatch={dispatch} />
            <DjangoTable setMessage={setMessage} models={assets} dispatch={dispatch} extraFields={extraFields} excludeFields={excludeFields} />
        
        </>
    );
}