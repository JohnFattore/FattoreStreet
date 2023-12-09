import React from 'react';
import axios from 'axios';

// variable to hold URL with Django Users API
const baseURLFinnhub = 'https://finnhub.io/api/v1/';

function AssetRow({ asset }) {
    const [quote, setQuote] =
        React.useState<{ numPrice: number; numPercentChange: number }>({
            numPrice: 0,
            numPercentChange: 0,
        });
    // Building API Call
    var type = "quote/"
    var getRequest = baseURLFinnhub + type

    // Get request to Finnhub for stock quote
    React.useEffect(() => {
        axios.get(getRequest, {
            params: {
                symbol: asset.ticker_string,
                token: "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"
            }
        }).then((response) => {
            setQuote({ numPrice: response.data.c, numPercentChange: response.data.dp });
        });
    }, [asset.ticker_string, getRequest]);

    if (!quote) return null;

    const numTotalCostBasis = asset.costbasis_number * asset.shares_number;
    const numTotalMarketPrice = quote.numPrice * asset.shares_number;
    const numTotalPercentChange = (numTotalMarketPrice - numTotalCostBasis) / numTotalCostBasis * 100

    var strColor = "red";
    if (numTotalPercentChange > 0) {
        strColor = "green";
    }
    
    return (
        <tr>
            <td>{asset.ticker_string}</td>
            <td>{asset.shares_number}</td>
            <td>${asset.costbasis_number}</td>
            <td>${numTotalCostBasis.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td>${numTotalMarketPrice.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td style={{color: strColor}}>{(numTotalPercentChange).toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}%</td>
            <td>{asset.buy_date}</td>
            <td>{asset.account_string}</td>
        </tr>
)}

export default AssetRow;