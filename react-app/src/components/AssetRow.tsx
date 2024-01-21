import React from 'react';
import axios from 'axios';
import { useContext } from 'react';
import { ENVContext } from './ENVContext';

function AssetRow({ asset, setChange}) {
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
                symbol: asset.ticker,
                token: ENV.finnhubKey
            }
        }).then((response) => {
            setQuote({ price: response.data.c, percentChange: response.data.dp });
        });
    }, [asset.ticker, ENV]);

    if (!quote) return null;

    const totalCostBasis = asset.costbasis * asset.shares;
    const totalMarketPrice = quote.price * asset.shares;
    const totalPercentChange = (totalMarketPrice - totalCostBasis) / totalCostBasis * 100

    var strColor = "red";
    if (totalPercentChange > 0) {
        strColor = "green";
    }

    return (
        <tr>
            <td>{asset.ticker}</td>
            <td>{asset.shares}</td>
            <td>${asset.costbasis}</td>
            <td>${totalCostBasis.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td>${totalMarketPrice.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td style={{color: strColor}}>{(totalPercentChange).toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}%</td>
            <td>{asset.buy}</td>
            <td  onClick={() => {
            axios.delete(ENV.djangoURL.concat("asset/", asset.id, "/"), {
                headers: {
                    'Authorization': ' Bearer '.concat(sessionStorage.getItem("token") as string)
                  }
            });
            setChange(true)
        }}>DELETE</td>
        </tr>
)}

export default AssetRow;