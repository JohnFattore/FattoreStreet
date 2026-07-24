import CompanyLogo from "./CompanyLogo";
import { Card, Row, Col } from "react-bootstrap";
import { useGetAssetInfosQuery } from "../functions/api";
export default function TickerHeader({ ticker }: { ticker: string }) {
  const { data: assetInfos } = useGetAssetInfosQuery([ticker]);
  const assetInfo = assetInfos ? assetInfos[ticker] : undefined;
  if (!assetInfo) {
    return null;
  }
  return (
    <Card className="ticker-header">
      <Card.Body>
        <Row>
          <Col>
            <div>
              {assetInfo?.type} • {ticker}
            </div>
            <Card.Title>{assetInfo?.longName}</Card.Title>
            <div>
              <span>${assetInfo?.currentPrice.toFixed(2)}</span>
              <span
                className={
                  assetInfo?.percentChangeDaily >= 0
                    ? "text-success"
                    : "text-danger"
                }
              >
                {assetInfo?.percentChangeDaily >= 0 ? "▲" : "▼"}{" "}
                {(assetInfo?.percentChangeDaily * 100).toFixed(2)}%
              </span>
            </div>
          </Col>
          {assetInfo?.type === "EQUITY" && (
            <Col>
              <CompanyLogo ticker={ticker} />
            </Col>
          )}
        </Row>
      </Card.Body>
    </Card>
  );
}
