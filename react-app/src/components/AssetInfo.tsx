import { useGetAssetInfosQuery } from "../functions/api";
import EquityInfo from "./EquityInfo";
import ETFInfo from "./ETFInfo";
import { Col, Alert } from "react-bootstrap";

export default function AssetInfo({ ticker }: { ticker: string }) {
  const { data: assetInfos } = useGetAssetInfosQuery([ticker]);
  const assetInfo = assetInfos?.[ticker];

  if (!assetInfo) {
    return <Alert variant="danger">No data available for {ticker}</Alert>;
  }

  return (
    <Col>
      <h3>General Financials</h3>
      {assetInfo?.type === "EQUITY" ? (
        <EquityInfo ticker={ticker} />
      ) : assetInfo?.type === "ETF" || "MUTUALFUND" ? (
        <ETFInfo ticker={ticker} />
      ) : null}
    </Col>
  );
}