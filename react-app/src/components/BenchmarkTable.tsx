import { Table, Spinner, Alert } from "react-bootstrap";
import { formatString, getErrorMessages } from "../functions/helperFunctions";
import { useGetAssetInfosQuery } from "../functions/api";

function BenchmarkRow({ benchmark }) {
  return (
    <tr>
      <td>{benchmark.ticker}</td>
      <td>{benchmark.shortName}</td>
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
  const benchmarkTickers = ["VT", "VTI", "VXUS", "VTWO", "BND", "VNQ", "UUP"];
  const { data: benchmarks, error } = useGetAssetInfosQuery(benchmarkTickers);

  if (error)
    return (
      <Alert variant="danger">{getErrorMessages(error["data"])}</Alert>
    );

  if (!benchmarks) return <Spinner animation="border" />;

  return (
    <Table>
      <thead>
        <tr>
          <th>Ticker</th>
          <th>ETF Name</th>
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
