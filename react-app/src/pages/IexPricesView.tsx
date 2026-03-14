import { useNavigate, useParams } from "react-router-dom";
import { Alert, Button, Container } from "react-bootstrap";
import { useGetIexPricesQuery } from "../functions/api";
import { getApiErrorMessages } from "../functions/helperFunctions";
import { SortableTable } from "../components/SortableTable";
import LoadingModal from "../components/LoadingModal";
import PriceComparison from "../components/PriceComparison";
import DividendComparison from "../components/DividendComparison";
import SplitComparison from "../components/SplitComparison";
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
    label: "Adj Open",
    sortKey: "adjustedOpen" as const,
    render: (row: IIexPrice) => formatCurrency(row.adjustedOpen ?? row.open),
  },
  {
    label: "Adj High",
    sortKey: "adjustedHigh" as const,
    render: (row: IIexPrice) => formatCurrency(row.adjustedHigh ?? row.high),
  },
  {
    label: "Adj Low",
    sortKey: "adjustedLow" as const,
    render: (row: IIexPrice) => formatCurrency(row.adjustedLow ?? row.low),
  },
  {
    label: "Adj Close",
    sortKey: "adjustedClose" as const,
    render: (row: IIexPrice) => formatCurrency(row.adjustedClose ?? row.close),
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
        {prices.length} trading day{prices.length !== 1 ? "s" : ""} of adjusted OHLCV data from IEX exchange prices.
      </p>
      {/*
      <SortableTable
        data={prices}
        columns={columns}
        initialSortKey="date"
        initialSortDirection="desc"
        isLoading={false}
        errors={[]}
      />
      */}
      <PriceComparison ticker={ticker} />
      <DividendComparison ticker={ticker} />
      <SplitComparison ticker={ticker} />
      <div className="mt-3 mb-3">
        <Button variant="secondary" onClick={() => navigate(-1)}>
          Back
        </Button>
      </div>
    </Container>
  );
}
