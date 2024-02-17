import { useState, useEffect } from 'react';
import axios from 'axios';
import { useContext } from 'react';
import { ENVContext } from './ENVContext';
import { getQuote } from './AxiosFunctions';
import { IQuote } from '../interfaces';

export default function AssetRow({ asset, setChange, setError,index }) {
    const ENV = useContext(ENVContext);

    const [quote, setQuote] = useState<IQuote>({ price: 0, percentChange: 0, });

    // Get request to Finnhub for stock quote
    useEffect(() => {
        getQuote(asset.ticker)
            .then((response) => {
                setQuote({ price: response.data.c, percentChange: response.data.dp });
            })
            .catch(() => {
                //alert("Error Loading Quote")
                setError("quote")
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
                axios.delete(ENV.djangoURL.concat("asset/", asset.id, "/"), {
                    headers: {
                        'Authorization': ' Bearer '.concat(sessionStorage.getItem("token") as string)
                    }
                });
                setChange(true)
            }}>DELETE</td>
        </tr>
    )
}