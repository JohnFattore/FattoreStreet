import Table from 'react-bootstrap/Table';
import { useQuote } from './customHooks';
import { formatString } from './helperFunctions';

function BenchmarkRow({ benchmark, fields }) {

    const quote = useQuote(benchmark.ticker)

    let attributes: any[] = [
        benchmark.name,
        benchmark.ticker,
        quote.price,
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
        { ticker: "VTI", name: "US Market" },
        { ticker: "VXUS", name: "Global Market Ex US" },
        { ticker: "VTWO", name: "US Small Cap" },
        { ticker: "BND", name: "US Investable Bond Market" },
        { ticker: "VNQ", name: "US Real Estate" },
        { ticker: "UUP", name: "US Dollar vs International Currency" }
    ]

    const fields = [
        { name: "Name", type: "text" },
        { name: "Ticker", type: "text" },
        { name: "Price", type: "money" },
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