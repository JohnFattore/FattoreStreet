import CompanyLogo from "./CompanyLogo";
import { Card } from "react-bootstrap";
import { useGetAssetInfosQuery } from "../functions/api";
export default function TickerHeader({ ticker }) {
  const { data: assetInfos } = useGetAssetInfosQuery([ticker])
  const assetInfo = assetInfos ? assetInfos[ticker] : undefined
  if (!assetInfo) {
    return null
  }
  return (
    <Card>
      <Card.Body>
        <Card.Title>{assetInfo?.longName}</Card.Title>
        <Card.Text>{assetInfo?.type}</Card.Text>
        {assetInfo?.type == "EQUITY" ? <CompanyLogo ticker={ticker} />: null}
      </Card.Body>
    </Card>
  );
}
