import { Alert, ListGroup } from "react-bootstrap";
import { formatString } from "../functions/helperFunctions";
import { useGetAssetInfosQuery } from "../functions/api";
import { IETFInfo } from "../interfaces";

export default function ETFInfo({ticker}) {
  
  const { data: assetInfos } = useGetAssetInfosQuery([ticker]);
  const assetInfo = assetInfos ? (assetInfos[ticker] as IETFInfo) : null;
  if (!assetInfo) return <Alert>Loading</Alert>
  return (
    <>
    <ListGroup>
        <ListGroup.Item>{"Market Capitalization: " + formatString(assetInfo.marketCap, "money")}</ListGroup.Item>
        <ListGroup.Item>{"Expense Ratio: " + formatString(assetInfo.expenseRatio, "percent")}</ListGroup.Item>
    </ListGroup>
    </>
  );
}