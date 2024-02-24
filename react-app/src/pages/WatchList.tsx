import WatchListForm from '../components/WatchListForm'
import WatchListTable from '../components/WatchListTable';
import { useState } from 'react';
import { Alert } from 'react-bootstrap';
import { IMessage } from '../interfaces';
import { setAlertVarient } from '../components/helperFunctions';

export default function WatchList() {
    // assets is changed in useEffect, so another variable is needed to not cause the useEffect function to loop infinitly
    const [message, setMessage] = useState<IMessage>({text: "", type: ""})
    const [tickers, setTickers] = useState([]);

    return (
        <>
            <WatchListTable setMessage={setMessage} tickers={tickers} setTickers={setTickers} />
            <WatchListForm setMessage={setMessage} setTickers={setTickers} />
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
        </>
    );
}