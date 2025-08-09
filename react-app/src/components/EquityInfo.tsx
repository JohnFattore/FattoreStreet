import { Alert, ListGroup, Spinner } from "react-bootstrap";
import { formatString, getErrorMessages } from "../functions/helperFunctions";
import { useGetAssetInfosQuery } from "../functions/api";
import { IEquityInfo } from "../interfaces";

export default function EquityInfo({ ticker }) {
  const {
    data: assetInfos,
    error,
    isLoading,
  } = useGetAssetInfosQuery([ticker]);
  if (isLoading) {
    return <Spinner animation="border" />;
  }

  if (error) {
    return <Alert variant="danger">{getErrorMessages(error["data"])}</Alert>;
  }

  const assetInfo = assetInfos ? (assetInfos[ticker] as IEquityInfo) : null;

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
          {"Market Capitalization: " +
            formatString(assetInfo.marketCap, "money")}
        </ListGroup.Item>
        <ListGroup.Item>
          {"Price to Earnings Ratio: " +
            formatString(assetInfo.trailingPE, "amount")}
        </ListGroup.Item>
        <ListGroup.Item>
          {"Income Trailing Twelve Months: " +
            formatString(assetInfo.incomeTTM, "money")}
        </ListGroup.Item>
        <ListGroup.Item>
          {"Revenue Trailing Twelve Months: " +
            formatString(assetInfo.revenueTTM, "money")}
        </ListGroup.Item>
        <ListGroup.Item>
          {"Net Margin Trailing Twelve Months: " +
            formatString(assetInfo.netMarginTTM, "percent")}
        </ListGroup.Item>
      </ListGroup>
    </>
  );
}
