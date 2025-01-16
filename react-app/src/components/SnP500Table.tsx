import Table from 'react-bootstrap/Table';
import { Alert } from 'react-bootstrap';
import { formatString } from './helperFunctions';
import { useSelector, useDispatch } from "react-redux";
import { RootState, AppDispatch } from '../main';
import { translateError } from './helperFunctions';
import { removeSnP500, setSnP500Sort } from '../reducers/snp500Reducer';

const fields = [
    { name: "Date", type: "text", field: "date" },
    { name: "Cost Basis", type: "money", field: "costBasis" },
    { name: "Current Price", type: "money", field: "currentPrice" },
    { name: "Percent Change", type: "percent", field: "percentChange" },
    { name: "Remove", type: "remove", field: "remove" },
]

function SnP500Row({ snp500, fields }) {
    const dispatch = useDispatch<AppDispatch>();
    let tableData: JSX.Element[] = [];

    for (let i = 0; i < fields.length; i++) {
        if (fields[i]["type"] == 'remove') {
            tableData.push(<td key={i} onClick={() => dispatch(removeSnP500(snp500.id))}>{"remove"}</td>)
        }
        else {
            tableData.push(<td key={i}>{formatString(snp500[fields[i]["field"]], fields[i]["type"])}</td>)
        }
    }

    return (
        <tr>
            {tableData}
        </tr>)
}

export default function SnP500Table() {

    const { snp500Prices, error, sort } = useSelector((state: RootState) => state.snp500Prices);
    const dispatch = useDispatch<AppDispatch>();

    const handleSort = (sortColumn: string) => {
        const sortDirection =
            sort.sortColumn === sortColumn && sort.sortDirection === 'asc'
                ? 'desc'
                : 'asc';
        dispatch(setSnP500Sort({ sortColumn, sortDirection }));
    };


    let headers: JSX.Element[] = []
    for (let i = 0; i < fields.length; i++) {
        headers.push(<th key={i} onClick={() => handleSort(fields[i]["field"])}>{fields[i].name}</th>)
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