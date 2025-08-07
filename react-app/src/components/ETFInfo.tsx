import { Alert, ListGroup, Spinner } from "react-bootstrap";
import { formatString, getErrorMessages } from "../functions/helperFunctions";
import { useGetAssetInfosQuery } from "../functions/api";
import { IETFInfo } from "../interfaces";

export default function ETFInfo({ ticker }) {
  const {
    data: assetInfos,
    isLoading,
    error,
  } = useGetAssetInfosQuery([ticker]);
  const assetInfo = assetInfos ? (assetInfos[ticker] as IETFInfo) : null;
  if (error)
    return (
      <Alert variant="danger">{getErrorMessages(error["data"])}</Alert>
    );
  if (!assetInfo) return <Spinner animation="border" />;
  if (isLoading) return <Spinner animation="border" />;
  return (
    <>
      <ListGroup>
        <ListGroup.Item>
          {"Market Capitalization: " +
            formatString(assetInfo.marketCap, "money")}
        </ListGroup.Item>
        <ListGroup.Item>
          {"Expense Ratio: " + formatString(assetInfo.expenseRatio, "percent")}
        </ListGroup.Item>
      </ListGroup>
    </>
  );
}
