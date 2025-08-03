import { Table, Spinner } from "react-bootstrap";
import { formatString } from "../functions/helperFunctions";
import { useGetAssetInfosQuery } from "../functions/api";

function BenchmarkRow({ benchmark }) {
  return (
    <tr>
      <td>{benchmark.ticker}</td>
      <td>{benchmark.shortName}</td>
      <td>{formatString(benchmark.percentChangeDaily, "percent")}</td>
    </tr>
  );
}

export default function BenchmarkTable() {
  const benchmarkTickers = ["VT", "VTI", "VXUS", "VTWO", "BND", "VNQ", "UUP"];
  const { data: benchmarks } = useGetAssetInfosQuery(benchmarkTickers);
  if (!benchmarks) return <Spinner animation="border" />;

  return (
    <Table>
      <thead>
        <tr>
          <th>Ticker</th>
          <th>ETF Name</th>
          <th>Percent Change Today</th>
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
