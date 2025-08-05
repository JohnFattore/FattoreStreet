import { useNavigate } from "react-router-dom";
import {
  Alert,
  Button,
} from "react-bootstrap";
import { useParams } from "react-router-dom";
import AssetInfo from "../components/AssetInfo";
import AssetTickerTable from "../components/AssetTickerTable";
import TickerHeader from "../components/TickerHeader";
import AssetTickerSoldTable from "../components/AssetTickerSoldTable";

export default function AssetView() {
  const navigate = useNavigate();
  const { ticker } = useParams<{ ticker: string }>();
  if (!ticker) {return <Alert>Loading</Alert>}

  return (
    <>
      <TickerHeader ticker={ticker}/>
      <AssetInfo ticker={ticker} />
      <AssetTickerTable ticker={ticker} />
      <AssetTickerSoldTable ticker={ticker} />
      <Button onClick={() => navigate("/portfolio")}>Back to Portfolio</Button>
      <Button onClick={() => navigate("/watchlist")}>Back to WatchList</Button>
    </>
  );
}