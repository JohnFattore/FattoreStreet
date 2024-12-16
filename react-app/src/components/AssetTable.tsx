import Table from 'react-bootstrap/Table';
import { useState } from 'react';
import { useQuote } from './customHooks';
import { formatString } from './helperFunctions';
import { deleteAsset } from './axiosFunctions';

// function is for simple calculations, function2 is for more complex operations
function AssetRow({ asset, dispatch, fields, setMessage }) {

    const quote = useQuote(asset.ticker, setMessage)["price"];
    const quoteSnP500 = useQuote("SPY", setMessage)["price"];
    const totalCostBasis = asset.shares * asset.costbasis;
    const totalMarketValue = asset.shares * quote;

    let attributes: any[] = [
        asset.ticker,
        asset.shares,
        asset.costbasis,
        quote,
        totalCostBasis,
        totalMarketValue,
        (quote - asset.costbasis) / asset.costbasis,
        (quoteSnP500 - asset.SnP500Price) / asset.SnP500Price,
        "delete"
    ];

    //for (let i = 0; i < attributes.length; i++) {
    //    formatString(attributes[i], fields[i]["type"])
    //}

    let tableData: JSX.Element[] = [];

    for (let i = 0; i < attributes.length; i++) {
        if (fields[i]["type"] == "delete")
            tableData.push(<td onClick={() => {
                deleteAsset(asset.id).then(() => {
                    dispatch({ type: "delete", asset: asset });
                    setMessage({ text: asset.ticker + " deleted", type: "success" })
                })
                    .catch(() => {
                        setMessage({ text: "There was a problem deleting the asset", type: "error" })
                    })
            }}>{formatString(attributes[i], fields[i]["type"])}</td>)
        else
            tableData.push(<td>{formatString(attributes[i], fields[i]["type"])}</td>)
    }

    return (
        <tr key={asset.id}>
            {tableData}
        </tr>)
}

export default function AssetTable({ assets, dispatch, setMessage }) {

    const fields = [
        { name: "Ticker", type: "text" },
        { name: "Shares", type: "amount" },
        { name: "Cost Basis", type: "money" },
        { name: "Quote", type: "money" },
        { name: "Total Cost Basis", type: "money" },
        { name: "Total Market Price", type: "money" },
        { name: "Percent Change", type: "percent" },
        { name: "S&P 500 % Change", type: "percent" },
        { name: "Delete", type: "delete"}
    ]
    
    const [counter, setCounter] = useState(1)
    let headers: JSX.Element[] = []
    for (const field in fields) {
        headers.push(<th onClick={() => {
            setCounter(counter * -1)
            if (fields[field].type == "text") {
                assets.sort((a, b) => {
                    if (b[field] > a[field])
                        return (counter * 1)
                    else if (b[field] < a[field])
                        return (counter * -1)
                    else
                        return 0
                })
            }
            else {
                assets.sort((a, b) => counter * (b[field] - a[field]))
            }
            dispatch({ type: "refresh" });
        }}>{fields[field].name}</th>);
    }

    if (assets.length == 0) {
        return (<h3 role="noModels">No Data</h3>)
    }

    // hard to not feel like models and rows is too similar
    return (
        <Table>
            <thead>
                <tr>
                    {headers}
                </tr>
            </thead>
            <tbody>
                {assets.map((asset) => (
                    <AssetRow asset={asset} dispatch={dispatch} fields={fields} setMessage={setMessage} />
                ))}
            </tbody>
        </Table>
    );
}