import Table from 'react-bootstrap/Table';
import { Alert } from 'react-bootstrap';
import { useQuote } from './customHooks';
import { formatString } from './helperFunctions';
import { useSelector } from "react-redux";
import { RootState } from '../main';
import { translateError } from './helperFunctions';
import { IAllocation } from '../interfaces';

// function is for simple calculations, function2 is for more complex operations
function AllocationRow({ allocation, fields }) {
    const quote = useQuote(allocation.ticker)["price"];

    let attributes: any[] = [
        allocation.ticker,
        allocation.shares,
        quote * allocation.shares
    ];

    let tableData: JSX.Element[] = [];

    for (let i = 0; i < attributes.length; i++) {
        tableData.push(<td>{formatString(attributes[i], fields[i]["type"])}</td>)
    }

    return (
        <tr key={allocation.id}>
            {tableData}
        </tr>)
}

export default function AllocationTable() {

    const { assets, error } = useSelector((state: RootState) => state.assets);

    const allocations: IAllocation[] = []
    for (let i = 0; i < assets.length; i++) {
        if (allocations.some((allocation) => allocation.ticker === (assets[i] as any).ticker)) {
            (allocations.find(({ ticker }) => ticker == (assets[i] as any).ticker) as IAllocation).shares = (allocations.find(({ ticker }) => ticker == (assets[i] as any).ticker) as IAllocation).shares + parseInt((assets[i] as any).shares)
        }
        else {
            allocations.push({ ticker: (assets[i] as any).ticker, shares: parseInt((assets[i] as any).shares) })
        }
    };
    const fields = [
        { name: "Ticker", type: "text" },
        { name: "Shares", type: "amount" },
        { name: "Total Market Price", type: "money" },
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
                {allocations.map((allocation) => (
                    <AllocationRow allocation={allocation} fields={fields} />
                ))}
            </tbody>
        </Table>
    );
}