import CompanyLogo from "./CompanyLogo";
import { Card, Row, Col } from "react-bootstrap";
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
        <Row className="align-items-center">
          <Col>
            <div>
              {assetInfo?.type} • {ticker}
            </div>
            <Card.Title className="display-4 fw-bold mb-2">
              {assetInfo?.longName}
            </Card.Title>
            <div>
              <span className="display-3 fw-bold">${assetInfo?.currentPrice.toFixed(2)}</span>
              <span className={`h2 mb-0 fw-semibold ${assetInfo?.percentChangeDaily >= 0 ? 'text-success' : 'text-danger'}`}>
                {assetInfo?.percentChangeDaily >= 0 ? '▲' : '▼'} {(assetInfo?.percentChangeDaily * 100).toFixed(2)}%
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
