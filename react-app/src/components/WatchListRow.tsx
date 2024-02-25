import { useState, useEffect } from 'react';
import { getQuote } from './AxiosFunctions';

export default function WatchListRow({ ticker, index, setMessage, setTickers }) {
    const [quote, setQuote] = useState<{ price: number; percentChange: number }>({ price: 0, percentChange: 0 });

    // Get request to Finnhub for stock quote
    useEffect(() => {

        // if ticker in localstorage and timestamp is less than 5 min ago
        let storedQuote = localStorage.getItem(ticker);
        if (storedQuote != null) {
            let listQuote = JSON.parse(storedQuote);
            setQuote({ price: listQuote[0], percentChange: listQuote[1] })
        }
        else {
            getQuote(ticker).then((response) => {
                    setQuote({ price: response.data.c, percentChange: response.data.dp });
                    localStorage.setItem(ticker, JSON.stringify([quote.price, quote.percentChange]));
                    // store in storage with ticker, stock data, and a time stamp
                }).catch(() => {
                    setMessage({ text: "We are experincing are issue getting some asset data", type: "error" })
                });
        }
    }, []);

    if (!quote) return null;

    var color = "red";
    if (quote.percentChange > 0) {
        color = "green";
    }

    return (
        <tr key={index}>
            <td role="ticker">{ticker}</td>
            <td>${quote.price.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td role="percentChange" style={{ color: color }}>{quote.percentChange.toLocaleString(undefined, {
                minimumFractionDigits: 3,
                maximumFractionDigits: 3
            })}%</td>
            <td role="delete" onClick={() => {
                let tickersDB = localStorage.getItem("tickers");
                let tickers: string[] = JSON.parse(tickersDB as string);
                tickers = tickers.filter(e => e !== ticker); // will remove ticker from list
                // setting tickers so display refreshes
                setTickers(tickers)
                tickersDB = JSON.stringify(tickers);
                localStorage.setItem("tickers", tickersDB);
                setMessage({ text: ticker + " deleted from watchlist", type: "success" });
            }}>delete</td>
        </tr>)
}