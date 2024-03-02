import AssetTable from '../components/AssetTable';
import AssetForm from '../components/AssetForm';
import Alert from 'react-bootstrap/Alert';
import { useState } from 'react';
import { IMessage } from '../interfaces';
import { setAlertVarient } from '../components/helperFunctions';
import { useReducer } from 'react';

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

export default function Portfolio() {
    const [message, setMessage] = useState<IMessage>({ text: "", type: "" })
    const [assets, dispatch] = useReducer(assetReducer, []);

    return (
        <>
            <h3>Add Assets</h3>
            <AssetForm setMessage={setMessage} dispatch={dispatch} />
            <h1 role="assetTableHeader">User's Portfolio</h1>
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
            <AssetTable setMessage={setMessage} assets={assets} dispatch={dispatch} />
        </>
    );
}