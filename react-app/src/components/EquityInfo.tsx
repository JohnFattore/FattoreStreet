import { Alert, ListGroup } from "react-bootstrap";
import { formatString } from "../functions/helperFunctions";
import { useGetAssetInfosQuery } from "../functions/api";
import { IEquityInfo } from "../interfaces";

export default function EquityInfo({ticker}) {
  
  const { data: assetInfos } = useGetAssetInfosQuery([ticker]);
  const assetInfo = assetInfos ? (assetInfos[ticker] as IEquityInfo) : null;
  if (!assetInfo) return <Alert>Loading</Alert>
  return (
    <>
    <ListGroup>
        <ListGroup.Item>{"Market Capitalization: " + formatString(assetInfo.marketCap, "money")}</ListGroup.Item>
        <ListGroup.Item>{"Income TTM: " + formatString(assetInfo.incomeTTM, "money")}</ListGroup.Item>
        <ListGroup.Item>{"Revenue TTM: " + formatString(assetInfo.revenueTTM, "money")}</ListGroup.Item>
        <ListGroup.Item>{"Net Margin TTM: " + formatString(assetInfo.netMarginTTM, "percent")}</ListGroup.Item>
    </ListGroup>
    </>
  );
}
