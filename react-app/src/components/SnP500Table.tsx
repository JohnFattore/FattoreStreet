import Table from 'react-bootstrap/Table';
import { Alert } from 'react-bootstrap';
import { useQuote } from './customHooks';
import { formatString } from './helperFunctions';
import { useSelector, useDispatch } from "react-redux";
import { RootState, AppDispatch } from '../main';
import { translateError } from './helperFunctions';
import { removeSnP500 } from '../reducers/snp500Reducer';

function SnP500Row({ snp500, fields }) {
    const dispatch = useDispatch<AppDispatch>();

    const quote = useQuote("SPY")["price"];
    const reinvestSharesPrice = snp500.reinvestShares * quote

    let attributes: any[] = [
        snp500.date,
        snp500.price,
        quote,
        snp500.dividends,
        snp500.reinvestShares,
        reinvestSharesPrice,
        (reinvestSharesPrice - snp500.price)/ snp500.price,
        "remove"
    ];

    let tableData: JSX.Element[] = [];

    for (let i = 0; i < attributes.length; i++) {
        if (fields[i]["type"] == 'remove') {
            tableData.push(<td key={i} onClick={() => dispatch(removeSnP500(snp500.id))}>{"remove"}</td>)
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

export default function SnP500Table() {

    const { snp500Prices, error } = useSelector((state: RootState) => state.snp500Prices);

    const fields = [
        { name: "Date", type: "text" },
        { name: "Cost Basis", type: "money" },
        { name: "Current Price", type: "money" },
        { name: "Dividends", type: "money" },
        { name: "Reinvest Shares", type: "amount" },
        { name: "Reinvested Shares Price", type: "money" },
        { name: "Total Percent Change", type: "percent" },
        { name: "Remove", type: "remove" },
    ]

    let headers: JSX.Element[] = []
    for (let i = 0; i < fields.length; i++) {
        headers.push(<th key={i}>{fields[i].name}</th>)
    }

    if (snp500Prices.length == 0) {
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
                    {snp500Prices.map((snp500) => (
                        <SnP500Row key={snp500.id} snp500={snp500} fields={fields} />
                    ))}
                </tbody>
            </Table>
        </>

    );
}