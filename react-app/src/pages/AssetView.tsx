import { useNavigate } from "react-router-dom";
import { Alert, Button } from "react-bootstrap";
import { useParams } from "react-router-dom";
import AssetInfo from "../components/AssetInfo";
import AssetTickerTable from "../components/AssetTickerTable";
import TickerHeader from "../components/TickerHeader";
import AssetTickerSoldTable from "../components/AssetTickerSoldTable";
import AssetForm from "../components/AssetForm";
import { useGetAssetInfosQuery, useGetAssetPricesQuery } from "../functions/api";
import { getApiErrorMessages } from "../functions/helperFunctions";
import GenericLineChart from "../components/GenericLineChart";
import LoadingModal from "../components/LoadingModal";

export default function AssetView() {
  const navigate = useNavigate();
  const { ticker } = useParams<{ ticker: string }>();
  if (!ticker) {
    return <Alert variant="danger">Error</Alert>;
  }
  const { isLoading, error } = useGetAssetInfosQuery([ticker]);
  const { data: prices } = useGetAssetPricesQuery(ticker)
  if (isLoading)
    return (
      <LoadingModal
        show={true}
        message={`Loading ${ticker} Data...`}
      />
    );
  if (error)
    return (
      <>
        <h3>{ticker} View</h3>
        <Alert variant="danger">{getApiErrorMessages(error)}</Alert>
      </>
    );
  return (
    <>
      <TickerHeader ticker={ticker} />
      <AssetInfo ticker={ticker} />
      <AssetTickerTable ticker={ticker} />
      <AssetTickerSoldTable ticker={ticker} />
      <GenericLineChart
        data={prices}
        label="Price history"
        description="Historical price performance for this asset over the selected period."
      />
      <AssetForm defaultTicker={ticker} />
      <Button variant="primary" onClick={() => navigate(`/sec-edgar/${ticker}`)}>SEC EDGAR Data</Button>
      <Button onClick={() => navigate("/portfolio")}>Back to Portfolio</Button>
      <Button onClick={() => navigate("/watchlist")}>Back to WatchList</Button>
    </>
  );
}
