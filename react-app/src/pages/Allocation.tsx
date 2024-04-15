import Alert from 'react-bootstrap/Alert';
import { useState, useEffect } from "react";
import { IMessage, IAllocation } from "../interfaces";
import { setAlertVarient } from "../components/helperFunctions";
import DjangoTable from "../components/DjangoTable";
import { getAssets } from "../components/axiosFunctions";
import { useQuote } from "../components/customHooks";

function multipy(num1, num2) {
        return num1 * num2
    }

export default function Allocation() {
        const [message, setMessage] = useState<IMessage>({ text: "", type: "" })
        const [assets, setAssets] = useState([]);

        // API call for user's owned assets
        useEffect(() => {
            getAssets()
                .then((response) => {
                    setAssets(response.data);
                })
                .catch(() => {
                    setMessage({text: "There was a problem getting assets", type: "error"}) 
                })
        }, []); 
    
        if (!assets) return null;
    
        if (assets.length == 0) {
            return (<h3 role='noAssets'>You don't own any assets</h3>)
        }
    
        const allocations: IAllocation[] = []
    
        // iterate over all assets, return a list of allocations
        for (let i = 0; i < assets.length; i++) {
            if (allocations.some((allocation) => allocation.ticker === (assets[i] as any).ticker)) {
                (allocations.find(({ ticker }) => ticker == (assets[i] as any).ticker) as IAllocation).shares = (allocations.find(({ ticker }) => ticker == (assets[i] as any).ticker) as IAllocation).shares + parseInt((assets[i] as any).shares)
            }
            else {
                allocations.push({ ticker: (assets[i] as any).ticker, shares: parseInt((assets[i] as any).shares) })
            }
        };

        const fields = {
                ticker: {name: "Ticker"},
                shares: {name: "Shares", type: "amount"},
                quote: {name: "Quote", function: useQuote, parameters: ['ticker', setMessage], item: "price", type: "hidden" },
                currentPrice: {name: "Current Price", function: multipy, parameters: ['shares', 'quote'], type: "money" },
        }

        return (
                <>
                        <h1 role="pageHeader">User's Allocation of Assets</h1>
                        {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
                        <DjangoTable setMessage={setMessage} models={allocations} dispatch={console.log} fields={fields} axiosFunctions={{}}/>
                </>

        );
}