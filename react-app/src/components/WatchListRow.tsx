import React from 'react';
import { Button } from 'react-bootstrap';
import { getQuote } from './AxiosFunctions';

function WatchListRow({ ticker, setChange }) {
    const [quote, setQuote] =
        React.useState<{ price: number; percentChange: number }>({
            price: 0,
            percentChange: 0,
        });

    // Get request to Finnhub for stock quote
    React.useEffect(() => {
        getQuote(ticker)
            .then((response) => {
                setQuote({ price: response.data.c, percentChange: response.data.dp });
            }).catch((response) => {
                console.log(response)
            });
    }, [ticker]);

    if (!quote) return null;

    var color = "red";
    if (quote.percentChange > 0) {
        color = "green";
    }

    return (
        <tr>
            <td>{ticker}</td>
            <td>${quote.price.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td style={{ color: color }}>{quote.percentChange.toLocaleString(undefined, {
                minimumFractionDigits: 3,
                maximumFractionDigits: 3
            })}%</td>
            <Button onClick={() => {
                let tickersDB = localStorage.getItem("tickers");
                let tickers: string[] = JSON.parse(tickersDB as string);
                if (tickers.includes(ticker)) {
                    tickers = tickers.filter(e => e !== ticker); // will remove ticker from list
                    tickersDB = JSON.stringify(tickers);
                    localStorage.setItem("tickers", tickersDB)
                    setChange(true)
                }
            }}>DELETE</Button>
        </tr>)
}

export default WatchListRow;