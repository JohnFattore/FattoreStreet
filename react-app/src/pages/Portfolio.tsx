import AssetTable from '../components/AssetTable';
import AssetForm from '../components/AssetForm';
import Alert from 'react-bootstrap/Alert';
import { useState } from 'react';
import { IMessage, IAsset } from '../interfaces';
import { setAlertVarient } from '../components/helperFunctions';

export default function Portfolio() {

    const [message, setMessage] = useState<IMessage>({text: "", type: ""})
    const [assets, setAssets] = useState<IAsset[]>([]);

    return (
        <>
            <h3>Add Assets</h3>
            <AssetForm setMessage={setMessage} assets={assets} setAssets={setAssets}/>
            <h1 role="assetTableHeader">User's Portfolio</h1>
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
            <AssetTable setMessage={setMessage} assets={assets} setAssets={setAssets}/>
        </>
    );
}