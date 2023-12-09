import React from 'react';
import axios from 'axios';

// variable to hold URL with Django Users API
const baseURLFinnhub = 'https://finnhub.io/api/v1/';

function WatchListRow({ strTicker }) {
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
                symbol: strTicker,
                token: "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"
            }
        }).then((response) => {
            setQuote({ numPrice: response.data.c, numPercentChange: response.data.dp });
        });
    }, [strTicker, getRequest]);

    if (!quote) return null;

    var strColor = "red";
    if (quote.numPercentChange > 0) {
        strColor = "green";
    }

    return (
        <tr>
            <td>{strTicker}</td>
            <td>${quote.numPrice.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td style={{color: strColor}}>{quote.numPercentChange.toLocaleString(undefined, {
                minimumFractionDigits: 3,
                maximumFractionDigits: 3
            })}%</td>
        </tr>)
}

export default WatchListRow;