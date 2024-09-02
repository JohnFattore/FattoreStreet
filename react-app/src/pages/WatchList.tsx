import WatchListForm from '../components/WatchListForm'
import { useState, useEffect } from 'react';
import { Alert } from 'react-bootstrap';
import { IMessage } from '../interfaces';
import { setAlertVarient } from '../components/helperFunctions';
import DjangoTable from '../components/DjangoTable';
import { useCompanyProfile2, useQuote } from '../components/customHooks';

export default function WatchList() {
    const [message, setMessage] = useState<IMessage>({ text: "", type: "" });
    // tickers stored as string, but handled as array of strings
    if (localStorage.getItem("tickers") == null) {
        let allTickers: string[] = (["VTI", "SPY"]);
        localStorage.setItem("tickers", (JSON.stringify(allTickers)));
    };

    const [tickerModels, setTickers] = useState<any[]>([]);
    useEffect(() => {
        if (tickerModels.length == 0) {
            const tickers = JSON.parse(localStorage.getItem("tickers") as string) as string[]
            for (const i in tickers) {
                setTickers(tickerModels => [...tickerModels as any[], { "ticker": tickers[i] }])
            }
        }
    }, [])

    //input of models is a list of dictionaries
    //const tickerModels = [{"ticker": "F"}]
    const fields = {
        ticker: { name: "Ticker" },
        quote: { name: "Quote", function: useQuote, parameters: ['ticker', setMessage], item: "price", type: "money" },
        percentChange: { name: "Daily Percent Change", function: useQuote, parameters: ['ticker', setMessage], item: "percentChange", type: "percent" },
        marketCap: { name: "Market Cap", function: useCompanyProfile2, parameters: ['ticker', setMessage], type: "marketCap" },
        delete: {name: "Delete", function2: setTickers, type: "axiosWL"}
    }

    const tickerModels2 = [{ ticker: "VTI", name: "US Market" },
    { ticker: "VXUS", name: "Global Market Ex US" },
    { ticker: "VTWO", name: "US Small Cap" },
    { ticker: "BND", name: "US Investable Bond Market" },
    { ticker: "VNQ", name: "US Real Estate" }
    ]

    const fields2 = {
        ticker: { name: "Ticker" },
        name: { name: "Name" },
        quote: { name: "Quote", function: useQuote, parameters: ['ticker', setMessage], item: "price", type: "hidden" },
        percentChange: { name: "Daily Percent Change", function: useQuote, parameters: ['ticker', setMessage], item: "percentChange", type: "percent" },
    }

    return (
        <>
            <h3>Common Benchmarks</h3>
            <DjangoTable models={tickerModels2} setMessage={setMessage} dispatch={console.log} fields={fields2} />
            <h3>Watchlist</h3>
            <DjangoTable models={tickerModels} setMessage={setMessage} dispatch={console.log} fields={fields} />
            <WatchListForm setMessage={setMessage} setTickers={setTickers} />
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
        </>
    );
}