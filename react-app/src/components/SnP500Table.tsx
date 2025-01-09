import Table from 'react-bootstrap/Table';
import { Alert } from 'react-bootstrap';
import { useQuote } from './customHooks';
import { formatString } from './helperFunctions';
import { useSelector } from "react-redux";
import { RootState } from '../main';
import { translateError } from './helperFunctions';

function SnP500Row({ snp500, fields }) {

    const quote = useQuote("SPY")["price"];

    let attributes: any[] = [
        snp500.date,
        snp500.price,
        quote,
        snp500.dividends,
        snp500.reinvestShares
    ];

    let tableData: JSX.Element[] = [];

    for (let i = 0; i < attributes.length; i++) {
        tableData.push(<td key={i}>{formatString(attributes[i], fields[i]["type"])}</td>)
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