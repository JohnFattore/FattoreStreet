import Table from 'react-bootstrap/Table';
import { Alert } from 'react-bootstrap';
import { useQuote } from './customHooks';
import { formatString } from './helperFunctions';
import { useSelector, useDispatch } from "react-redux";
import { AppDispatch, RootState } from '../main';
import { translateError } from './helperFunctions';
import { deleteAsset } from './axiosFunctions';

// function is for simple calculations, function2 is for more complex operations
function AssetRow({ asset, fields }) {
    const dispatch = useDispatch<AppDispatch>();

    const quote = useQuote(asset.ticker)["price"];
    const quoteSnP500 = useQuote("SPY")["price"];
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

    let tableData: JSX.Element[] = [];

    for (let i = 0; i < attributes.length; i++) {
        if (fields[i]["type"] == 'delete') {
            tableData.push(<td onClick={() => dispatch(deleteAsset(asset.id))}>{"delete"}</td>)
        }
        else {
            tableData.push(<td>{formatString(attributes[i], fields[i]["type"])}</td>)
        }
    }

    return (
        <tr key={asset.id}>
            {tableData}
        </tr>)
}

export default function AssetTable() {

    const { assets, error } = useSelector((state: RootState) => state.assets);

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
    
    let headers: JSX.Element[] = []
    for (let i = 0; i < fields.length; i++) {
        headers.push(<th key={i}>{fields[i].name}</th>)
    }

    //if (loading) return <Alert>Loading Assets</Alert>;
    if (error) return <Alert variant="danger">{translateError(error)}</Alert>;

    if (assets.length == 0) {
        return (<h3 role="noModels">No Data</h3>)
    }

    return (
        <Table>
            <thead>
                <tr>
                    {headers}
                </tr>
            </thead>
            <tbody>
                {assets.map((asset) => (
                    <AssetRow asset={asset} fields={fields} />
                ))}
            </tbody>
        </Table>
    );
}