import React from 'react';
import { useState } from 'react';
import { getQuote } from './AxiosFunctions';

export default function AllocationRow({ allocation, setError }) {
    const [quote, setQuote] =
        useState<{ price: number; percentChange: number }>({ price: 0, percentChange: 0, });

    // Get request to Finnhub for stock quote
    React.useEffect(() => {
        getQuote(allocation.ticker)
            .then((response) => {
                setQuote({ price: response.data.c, percentChange: response.data.dp });
            }).catch(() =>
                setError("Error getting assets")
            );
    }, [allocation.ticker]);

    if (!quote) return null;

    return (
        <tr>
            <td role='ticker'>{allocation.ticker}</td>
            <td role='shares'>{allocation.shares.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td role='price'>${(allocation.shares * quote.price).toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
        </tr>
    );
}