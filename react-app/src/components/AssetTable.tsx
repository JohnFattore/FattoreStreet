import Table from 'react-bootstrap/Table';
import { Alert } from 'react-bootstrap';
import { useQuote } from './customHooks';
import { formatString } from './helperFunctions';
import { useSelector, useDispatch } from "react-redux";
import { AppDispatch, RootState } from '../main';
import { translateError } from './helperFunctions';
import { deleteAsset } from './axiosFunctions';

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
            tableData.push(<td key={i} onClick={() => dispatch(deleteAsset(asset.id))}>{"delete"}</td>)
        }
        else {
            tableData.push(<td key={i}>{formatString(attributes[i], fields[i]["type"])}</td>)
        }
    }

    return (
        <tr>
            {tableData}
        </tr>)
}

export default function AssetTable() {

    const { assets, error } = useSelector((state: RootState) => state.assets);
    const { access } = useSelector((state: RootState) => state.user)

    const fields = [
        { name: "Ticker", type: "text" },
        { name: "Shares", type: "amount" },
        { name: "Cost Basis", type: "money" },
        { name: "Quote", type: "money" },
        { name: "Total Cost Basis", type: "money" },
        { name: "Total Market Price", type: "money" },
        { name: "Percent Change", type: "percent" },
        { name: "S&P 500 % Change", type: "percent" },
        { name: "Delete", type: "delete" }
    ]

    let headers: JSX.Element[] = []
    for (let i = 0; i < fields.length; i++) {
        headers.push(<th key={i}>{fields[i].name}</th>)
    }

    if (!access) {
        return (<Alert variant='danger'>Please Login</Alert>)
    }

    if (assets.length == 0 && access) {
        return (<Alert variant='danger'>No Data</Alert>)
    }

    return (
        <>
            {error && <Alert variant="danger">{translateError(error)}</Alert>}
            <Table>
                <thead>
                    <tr>
                        {headers}
                    </tr>
                </thead>
                <tbody>
                    {assets.map((asset) => (
                        <AssetRow key={asset.id} asset={asset} fields={fields} />
                    ))}
                </tbody>
            </Table>
        </>

    );
}