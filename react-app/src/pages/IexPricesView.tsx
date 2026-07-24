import { useNavigate, useParams } from "react-router-dom";
import { Alert, Button, Container } from "react-bootstrap";
import { useGetIexPricesQuery } from "../functions/api";
import { getApiErrorMessages } from "../functions/helperFunctions";
import LoadingModal from "../components/LoadingModal";
import PriceComparison from "../components/PriceComparison";
import DividendComparison from "../components/DividendComparison";
import SplitComparison from "../components/SplitComparison";

export default function IexPricesView() {
  const navigate = useNavigate();
  const { ticker } = useParams<{ ticker: string }>();

  const { data, isLoading, error } = useGetIexPricesQuery(ticker ?? "", {
    skip: !ticker,
  });

  if (!ticker) {
    return <Alert variant="danger">No ticker specified.</Alert>;
  }

  if (isLoading) {
    return (
      <LoadingModal
        show={true}
        message={`Loading IEX prices for ${ticker}...`}
      />
    );
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
        {prices.length} trading day{prices.length !== 1 ? "s" : ""} of adjusted
        OHLCV data from IEX exchange prices.
      </p>
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
