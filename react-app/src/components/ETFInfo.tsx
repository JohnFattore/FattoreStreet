import { Alert, ListGroup } from "react-bootstrap";
import { formatString } from "../functions/helperFunctions";
import { useGetAssetInfosQuery } from "../functions/api";
import { IETFInfo } from "../interfaces";
import StateHandler from "./StateHandler";

export default function ETFInfo({ ticker }: { ticker: string }) {
  const {
    data: assetInfos,
    isLoading,
    error,
  } = useGetAssetInfosQuery([ticker]);

  const assetInfo = assetInfos ? (assetInfos[ticker] as IETFInfo) : null;

  return (
    <StateHandler
      isLoading={isLoading}
      errors={[error]}
      content={
        !assetInfo ? (
          <Alert variant="warning">No data available for {ticker}</Alert>
        ) : (
          <ListGroup>
            <ListGroup.Item>
              {"Current Price: " +
                formatString(assetInfo.currentPrice, "money")}
            </ListGroup.Item>
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
              {"Expense Ratio: " +
                formatString(assetInfo.expenseRatio, "percent")}
            </ListGroup.Item>
            <ListGroup.Item>
              {"Annual Dividend Yield: " +
                formatString(assetInfo.dividendYield / 100, "percent")}
            </ListGroup.Item>
          </ListGroup>
        )
      }
    />
  );
}
