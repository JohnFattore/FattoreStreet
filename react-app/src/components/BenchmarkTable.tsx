import Table from 'react-bootstrap/Table';
import { useQuote } from './customHooks';
import { formatString } from './helperFunctions';

function BenchmarkRow({ benchmark, fields }) {

    const quote = useQuote(benchmark.ticker)

    let attributes: any[] = [
        benchmark.name,
        benchmark.ticker,
        quote.percentChange / 100,
    ];

    let tableData: JSX.Element[] = [];

    for (let i = 0; i < attributes.length; i++) {
        tableData.push(<td>{formatString(attributes[i], fields[i]["type"])}</td>)
    }

    return (
        <tr key={1}>
            {tableData}
        </tr>)
}

export default function BenchmarkTable() {

    const benchmarks = [
        { ticker: "VT", name: "Global Market", index: "FTSE Global All Cap Index" },
        { ticker: "VTI", name: "US Market", index: "CRSP US Total Market Index" },
        { ticker: "VXUS", name: "Global Market Ex US", index: "FTSE Global All Cap ex US Index" },
        { ticker: "VTWO", name: "US Small Cap", index: "Russell 2000 Index" },
        { ticker: "BND", name: "US Investable Bond Market", index: "Bloomberg U.S. Aggregate Float Adjusted Index" },
        { ticker: "VNQ", name: "US Real Estate", index: "MSCI US Investable Market Real Estate 25/50 Index" },
        { ticker: "UUP", name: "US Dollar vs International Currency", index: "US Dollar Index (DXY)" }
    ]

    const fields = [
        { name: "Name", type: "text" },
        { name: "Ticker", type: "text" },
        { name: "Percent Change", type: "percent" },
    ]

    let headers: JSX.Element[] = []
    for (let i = 0; i < fields.length; i++) {
        headers.push(<th key={i}>{fields[i].name}</th>)
    }

    if (benchmarks.length == 0) return (<h3>No Data</h3>)

    return (
        <Table>
            <thead>
                <tr>
                    {headers}
                </tr>
            </thead>
            <tbody>
                {benchmarks.map((benchmark) => (
                    <BenchmarkRow benchmark={benchmark} fields={fields} />
                ))}
            </tbody>
        </Table>
    );
}