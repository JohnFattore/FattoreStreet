import WatchListForm from '../components/WatchListForm'
import WatchListTable from '../components/WatchListTable';
import { useState } from 'react';
import { Alert } from 'react-bootstrap';
import { IMessage } from '../interfaces';
import { setAlertVarient } from '../components/helperFunctions';

export default function WatchList() {
    const [message, setMessage] = useState<IMessage>({text: "", type: ""});
    if (localStorage.getItem("tickers") == null) {
        let allTickers: string[] = (["VTI", "SPY"]);
        let tickersDB: string = (JSON.stringify(allTickers));
        localStorage.setItem("tickers", tickersDB);
      };
    const tickersDB = (localStorage.getItem("tickers") as string);
    const [tickers, setTickers] = useState<string[]>(JSON.parse(tickersDB) as string[]);
    // tickers stored as string, but handled as array of strings

    return (
        <>
            <WatchListTable setMessage={setMessage} tickers={tickers} setTickers={setTickers} />
            <WatchListForm setMessage={setMessage} setTickers={setTickers} />
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
        </>
    );
}