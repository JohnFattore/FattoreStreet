import { useState, useEffect } from 'react';
import { deleteAsset, getQuote } from './AxiosFunctions';
import { IQuote } from '../interfaces';

export default function AssetRow({ asset, setMessage, assets, setAssets }) {

    const [quote, setQuote] = useState<IQuote>({ price: 0, percentChange: 0, });

    // Get request to Finnhub for stock quote
    useEffect(() => {
        getQuote(asset.ticker)
            .then((response) => {
                setQuote({ price: response.data.c, percentChange: response.data.dp });
            })
            .catch(() => {
                setMessage({ text: "error getting some stock prices", type: "error" })
            });
    }, []);

    if (!quote) return null;

    const totalCostBasis = asset.costbasis * asset.shares;
    const totalMarketPrice = quote.price * asset.shares;
    const totalPercentChange = (totalMarketPrice - totalCostBasis) / totalCostBasis * 100

    var strColor = "red";
    if (totalPercentChange > 0) {
        strColor = "green";
    }

    return (
        <tr key={asset.id}>
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
                deleteAsset(asset.id).then(() => {
                    let allAssets = assets;
                    allAssets = allAssets.filter(e => e !== asset);
                    setAssets(allAssets);
                    setMessage({ text: asset.ticker + " deleted", type: "success" })
                })
                .catch(() => {
                    setMessage({ text: "There was a problem deleting the asset", type: "error" })
                })
            }}>DELETE</td>
        </tr>
    )
}