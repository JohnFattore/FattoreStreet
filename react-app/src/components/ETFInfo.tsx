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
  if (isLoading) {
    return <Spinner animation="border" />;
  }

  if (error) {
    return <Alert variant="danger">{getErrorMessages(error["data"])}</Alert>;
  }

  const assetInfo = assetInfos ? (assetInfos[ticker] as IETFInfo) : null;

  if (!assetInfo) {
    return <Alert variant="warning">No data available for {ticker}</Alert>;
  }

  return (
    <>
      <ListGroup>
        <ListGroup.Item>
          {"Percent Change Today: " +
            formatString(assetInfo.percentChangeDaily, "percent")}
        </ListGroup.Item>
        <ListGroup.Item>
          {"Percent Change Weekly: " +
            formatString(assetInfo.percentChangeWeekly, "percent")}
        </ListGroup.Item>
        <ListGroup.Item>
          {"Percent Change Monthly: " +
            formatString(assetInfo.percentChangeMonthly, "percent")}
        </ListGroup.Item>
        <ListGroup.Item>
          {"Percent Change Year To Date: " +
            formatString(assetInfo.percentChangeYTD, "percent")}
        </ListGroup.Item>
        <ListGroup.Item>
          {"Percent Change 1 Year: " +
            formatString(assetInfo.percentChangeYearly, "percent")}
        </ListGroup.Item>
        <ListGroup.Item>
          {"Percent Change 3 Years: " +
            formatString(assetInfo.percentChange3Years, "percent")}
        </ListGroup.Item>
        <ListGroup.Item>
          {"Percent Change 5 Years: " +
            formatString(assetInfo.percentChange5Years, "percent")}
        </ListGroup.Item>
        <ListGroup.Item>
          {"Expense Ratio: " + formatString(assetInfo.expenseRatio, "percent")}
        </ListGroup.Item>
      </ListGroup>
    </>
  );
}
