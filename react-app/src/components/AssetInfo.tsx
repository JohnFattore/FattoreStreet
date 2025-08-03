import { useGetAssetInfosQuery } from "../functions/api";
import EquityInfo from "./EquityInfo";
import ETFInfo from "./ETFInfo";
import { Col } from "react-bootstrap";

export default function AssetInfo({ ticker }: { ticker: string }) {
  const { data: assetInfos } = useGetAssetInfosQuery([ticker]);
  const assetInfo = assetInfos?.[ticker];

  return (
    <Col>
      <h3>General Financials</h3>
      {assetInfo?.type === "EQUITY" ? (
        <EquityInfo ticker={ticker} />
      ) : assetInfo?.type === "ETF" ? (
        <ETFInfo ticker={ticker} />
      ) : null}
    </Col>
  );
}