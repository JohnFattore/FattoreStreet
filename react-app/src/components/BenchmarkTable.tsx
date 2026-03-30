import { Table, Spinner, Alert } from "react-bootstrap";
import { formatString, getApiErrorMessages } from "../functions/helperFunctions";
import { useGetAssetInfosQuery } from "../functions/api";
import { IEquityInfo, IETFInfo } from "../interfaces";

const benchmarkNames: Record<string, string> = {
  "VT": "Total Global Stock Market",
  "VTI": "Total US Stock Market",
  "VXUS": "Total Global Stock Market Excluding US",
  "VTWO": "Russell 2000 / Small Cap US",
  "VGK": "Broad Europe (FTSE Developed Europe)",
  "BND": "Total US Bond Market",
  "UUP": "Dollar vs Foreign Currency"
};

function BenchmarkRow({ benchmark }: { benchmark: IEquityInfo | IETFInfo }) {
  return (
    <tr>
      <td>{benchmark.ticker}</td>
      <td>{benchmark.shortName}</td>
      <td>{benchmarkNames[benchmark.ticker]}</td>
      <td>{formatString(benchmark.percentChangeDaily, "percent")}</td>
      <td>{formatString(benchmark.percentChangeWeekly, "percent")}</td>
      <td>{formatString(benchmark.percentChangeMonthly, "percent")}</td>
      <td>{formatString(benchmark.percentChangeYTD, "percent")}</td>
      <td>{formatString(benchmark.percentChangeYearly, "percent")}</td>
      <td>{formatString(benchmark.percentChange3Years, "percent")}</td>
      <td>{formatString(benchmark.percentChange5Years, "percent")}</td>
    </tr>
  );
}

export default function BenchmarkTable() {
  const benchmarkTickers = ["VT", "VTI", "VXUS", "VTWO", "VGK", "BND", "UUP"];
  const { data: benchmarks, error } = useGetAssetInfosQuery(benchmarkTickers);

  if (error)
    return (
      <Alert variant="danger">{getApiErrorMessages(error)}</Alert>
    );

  if (!benchmarks) return <Spinner animation="border" />;

  return (
    <Table>
      <thead>
        <tr>
          <th>Ticker</th>
          <th>ETF Name</th>
          <th>Benchmark Name</th>
          <th>Percent Change Today</th>
          <th>Percent Change Weekly</th>
          <th>Percent Change Monthly</th>
          <th>Percent Change YTD</th>
          <th>Percent Change 1 Year</th>
          <th>Percent Change 3 Years</th>
          <th>Percent Change 5 Years</th>
        </tr>
      </thead>
      <tbody>
        {Object.entries(benchmarks).map(([ticker, benchmark]) => (
          <BenchmarkRow key={ticker} benchmark={benchmark} />
        ))}
      </tbody>
    </Table>
  );
}
