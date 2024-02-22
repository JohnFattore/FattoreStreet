import AssetTable from '../components/AssetTable';
import AssetForm from '../components/AssetForm';
import Alert from 'react-bootstrap/Alert';
import { useState } from 'react';

export default function Portfolio() {
    const [change, setChange] = useState(false)
    const [message, setMessage] = useState("noMessage")

    return (
        <>
            <h3>Add Assets</h3>
            <AssetForm setChange={setChange} setMessage={setMessage} />
            <h1 role="assetTableHeader">User's Portfolio</h1>
            {message.includes("Error") && <Alert variant='danger' dismissible transition role="errorMessage">{message} </Alert>}
            {message.includes("Success") && <Alert variant='success' dismissible transition role="successMessage">{message} </Alert>}
            <AssetTable change={change} setChange={setChange} setMessage={setMessage} />
        </>

    );
}