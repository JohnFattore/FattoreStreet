import React from 'react';
import axios from 'axios';
import { useContext } from 'react';
import { ENVContext } from './ENVContext';

function AllocationRow({allocation}) {
    const ENV = useContext(ENVContext);

    const [quote, setQuote] =
        React.useState<{ price: number; percentChange: number }>({
            price: 0,
            percentChange: 0,
        });

    // Get request to Finnhub for stock quote
    React.useEffect(() => {
        axios.get(ENV.finnhubURL.concat("quote/"), {
            params: {
                symbol: allocation.ticker,
                token: ENV.finnhubKey
            }
        }).then((response) => {
            setQuote({ price: response.data.c, percentChange: response.data.dp });
        });
    }, [allocation.ticker, ENV]);

    if (!quote) return null;
    
    return (
        <tr>
            <td>{allocation.ticker}</td>
            <td>{allocation.shares.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td>${(allocation.shares * quote.price).toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
        </tr>
        );
}

export default AllocationRow;