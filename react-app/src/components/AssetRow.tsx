import { useState, useEffect } from 'react';
import { deleteAsset, getQuote } from './AxiosFunctions';
import { IQuote } from '../interfaces';

export default function AssetRow({ asset, setChange, setMessage, index }) {

    const [quote, setQuote] = useState<IQuote>({ price: 0, percentChange: 0, });

    // Get request to Finnhub for stock quote
    useEffect(() => {
        getQuote(asset.ticker)
            .then((response) => {
                setQuote({ price: response.data.c, percentChange: response.data.dp });
            })
            .catch(() => {
                setMessage("error getting some stock prices")
            });
    }, [asset.ticker]);

    if (!quote) return null;

    const totalCostBasis = asset.costbasis * asset.shares;
    const totalMarketPrice = quote.price * asset.shares;
    const totalPercentChange = (totalMarketPrice - totalCostBasis) / totalCostBasis * 100

    var strColor = "red";
    if (totalPercentChange > 0) {
        strColor = "green";
    }

    return (
        <tr key={index}>
            <td role="ticker">{asset.ticker}</td>
            <td role="shares">{asset.shares}</td>
            <td role="costbasis">${asset.costbasis}</td>
            <td>${totalCostBasis.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td>${totalMarketPrice.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td style={{ color: strColor }}>{(totalPercentChange).toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}%</td>
            <td role="buy">{asset.buy}</td>
            <td onClick={() => {
                deleteAsset(asset.id)
                setChange(true)
            }}>DELETE</td>
        </tr>
    )
}