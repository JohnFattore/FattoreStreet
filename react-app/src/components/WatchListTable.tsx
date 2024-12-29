import Table from 'react-bootstrap/Table';
import { Alert } from 'react-bootstrap';
import { useCompanyProfile2, useQuote } from './customHooks';
import { formatString } from './helperFunctions';
import { useSelector, useDispatch } from "react-redux";
import { RootState, AppDispatch } from '../main';
import { translateError } from './helperFunctions';
import { removeTicker } from '../reducers/watchListReducer';

// function is for simple calculations, function2 is for more complex operations
function WatchListRow({ ticker, fields }) {
    const dispatch = useDispatch<AppDispatch>();

    const quote = useQuote(ticker)
    const marketCap = useCompanyProfile2(ticker)

    let attributes: any[] = [
        ticker,
        quote.price,
        quote.percentChange / 100,
        marketCap,
        "delete"
    ];

    let tableData: JSX.Element[] = [];

    for (let i = 0; i < attributes.length; i++) {
        if (fields[i]["type"] == 'delete') {
            tableData.push(<td onClick={() =>
                dispatch(removeTicker(ticker))
            }>{formatString(attributes[i], fields[i]["type"])}</td>)
        }
        else {
            tableData.push(<td>{formatString(attributes[i], fields[i]["type"])}</td>)
        }

    }

    return (
        <tr key={1}>
            {tableData}
        </tr>)
}

export default function WatchListTable() {

    const { tickers, loading, error } = useSelector((state: RootState) => state.watchList);

    const fields = [
        { name: "Ticker", type: "text" },
        { name: "Price", type: "money" },
        { name: "Percent Change", type: "percent" },
        { name: "Market Cap", type: "marketCap" },
        { name: "Delete", type: "delete"}
    ]
    
    let headers: JSX.Element[] = []
    for (let i = 0; i < fields.length; i++) {
        headers.push(<th key={i}>{fields[i].name}</th>)
    }

    if (loading) return <Alert>Loading WatchList</Alert>;
    if (error) return <Alert variant="danger">{translateError(error)}</Alert>;

    if (tickers.length == 0) {
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
                {tickers.map((ticker) => (
                    <WatchListRow ticker={ticker} fields={fields} />
                ))}
            </tbody>
        </Table>
    );
}