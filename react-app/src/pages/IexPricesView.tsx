import { useNavigate, useParams } from "react-router-dom";
import { Alert, Button, Container } from "react-bootstrap";
import { useGetIexPricesQuery } from "../functions/api";
import { getApiErrorMessages } from "../functions/helperFunctions";
import { SortableTable } from "../components/SortableTable";
import LoadingModal from "../components/LoadingModal";
import PriceComparison from "../components/PriceComparison";
import { IIexPrice } from "../interfaces";

const formatCurrency = (value: number | null | undefined) =>
  value != null
    ? new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD",
        minimumFractionDigits: 2,
        maximumFractionDigits: 4,
      }).format(value)
    : "—";

const formatVolume = (value: number | null | undefined) =>
  value != null ? new Intl.NumberFormat("en-US").format(value) : "—";

const columns = [
  { label: "Date", sortKey: "date" as const },
  {
    label: "Open",
    sortKey: "open" as const,
    render: (row: IIexPrice) => formatCurrency(row.open),
  },
  {
    label: "High",
    sortKey: "high" as const,
    render: (row: IIexPrice) => formatCurrency(row.high),
  },
  {
    label: "Low",
    sortKey: "low" as const,
    render: (row: IIexPrice) => formatCurrency(row.low),
  },
  {
    label: "Close",
    sortKey: "close" as const,
    render: (row: IIexPrice) => formatCurrency(row.close),
  },
  {
    label: "Volume",
    sortKey: "volume" as const,
    render: (row: IIexPrice) => formatVolume(row.volume),
  },
];

export default function IexPricesView() {
  const navigate = useNavigate();
  const { ticker } = useParams<{ ticker: string }>();

  if (!ticker) {
    return <Alert variant="danger">No ticker specified.</Alert>;
  }

  const { data, isLoading, error } = useGetIexPricesQuery(ticker);

  if (isLoading) {
    return <LoadingModal show={true} message={`Loading IEX prices for ${ticker}...`} />;
  }

  if (error) {
    return (
      <Container className="mt-3">
        <h3>{ticker} — IEX Price History</h3>
        <Alert variant="danger">{getApiErrorMessages(error)}</Alert>
        <Button variant="secondary" onClick={() => navigate(-1)}>
          Back
        </Button>
      </Container>
    );
  }

  const prices = data?.prices ?? [];

  return (
    <Container className="mt-3">
      <h3>{ticker} — IEX Daily Prices</h3>
      <p className="text-muted">
        {prices.length} trading day{prices.length !== 1 ? "s" : ""} from IEX exchange data.
      </p>
      <SortableTable
        data={prices}
        columns={columns}
        initialSortKey="date"
        initialSortDirection="desc"
        isLoading={false}
        errors={[]}
      />
      <PriceComparison ticker={ticker} />
      <div className="mt-3 mb-3">
        <Button variant="secondary" onClick={() => navigate(-1)}>
          Back
        </Button>
      </div>
    </Container>
  );
}
