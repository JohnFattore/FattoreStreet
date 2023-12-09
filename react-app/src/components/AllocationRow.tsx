import React from 'react';
import axios from 'axios';

// variable to hold URL with Django Users API
const baseURLFinnhub = 'https://finnhub.io/api/v1/';

function AllocationRow({allocation}) {
    //const [strTicker, setTicker] = React.useState("SPY");
    console.log(allocation)
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
                symbol: allocation.strTicker,
                token: "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"
            }
        }).then((response) => {
            setQuote({ numPrice: response.data.c, numPercentChange: response.data.dp });
        });
    }, [allocation.strTicker, getRequest]);

    if (!quote) return null;


    
    return (
        <tr>
            <td>{allocation.strTicker}</td>
            <td>{allocation.numShares.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td>${(allocation.numShares * quote.numPrice).toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
        </tr>
        );
}

export default AllocationRow;