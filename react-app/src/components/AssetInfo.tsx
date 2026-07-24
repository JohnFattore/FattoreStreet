import {
  useGetAssetInfosQuery,
  useGetSecEdgarDataQuery,
} from "../functions/api";
import EquityInfo from "./EquityInfo";
import ETFInfo from "./ETFInfo";
import CompanyOverview from "./CompanyOverview";
import TTMIncomeStatement from "./TTMIncomeStatement";
import LatestBalanceSheet from "./LatestBalanceSheet";
import FinancialRatios from "./FinancialRatios";
import { Col, Row, Alert } from "react-bootstrap";

export default function AssetInfo({ ticker }: { ticker: string }) {
  const { data: assetInfos } = useGetAssetInfosQuery([ticker]);
  const assetInfo = assetInfos?.[ticker];
  const isEquity = assetInfo?.type === "EQUITY";
  const { data: secData } = useGetSecEdgarDataQuery(ticker, {
    skip: !isEquity,
  });

  if (!assetInfo) {
    return <Alert variant="danger">No data available for {ticker}</Alert>;
  }

  return (
    <Col>
      <h3>General Financials</h3>
      {isEquity ? (
        <>
          <EquityInfo ticker={ticker} />
          {secData && (
            <>
              <CompanyOverview data={secData} />
              <Row>
                <Col md={6}>
                  <TTMIncomeStatement data={secData} />
                </Col>
                <Col md={6}>
                  <LatestBalanceSheet data={secData} />
                </Col>
              </Row>
              <FinancialRatios data={secData} />
            </>
          )}
        </>
      ) : assetInfo?.type === "ETF" || assetInfo?.type === "MUTUALFUND" ? (
        <ETFInfo ticker={ticker} />
      ) : null}
    </Col>
  );
}
