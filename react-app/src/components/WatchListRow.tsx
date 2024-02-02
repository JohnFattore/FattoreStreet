import React from 'react';
import axios from 'axios';
import { ENVContext } from './ENVContext';

function WatchListRow({ ticker, setChange }) {
    const ENV = React.useContext(ENVContext);

    const [quote, setQuote] =
        React.useState<{ price: number; percentChange: number }>({
            price: 0,
            percentChange: 0,
        });

    // Get request to Finnhub for stock quote
    React.useEffect(() => {
        axios.get(ENV.finnhubURL.concat("quote/"), {
            params: {
                symbol: ticker,
                token: ENV.finnhubKey
            }
        }).then((response) => {
            setQuote({ price: response.data.c, percentChange: response.data.dp });
        }).catch((response) => {
            console.log(response)
        });
    }, [ticker, ENV]);

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
            <td onClick={() => {
                let tickersDB = localStorage.getItem("tickers");
                let tickers: string[] = JSON.parse(tickersDB as string);
                if (tickers.includes(ticker)) {
                    tickers = tickers.filter(e => e !== ticker); // will remove ticker from list
                    tickersDB = JSON.stringify(tickers);
                    localStorage.setItem("tickers", tickersDB)
                    setChange(true)
                }
            }}>DELETE</td>
        </tr>)
}

export default WatchListRow;