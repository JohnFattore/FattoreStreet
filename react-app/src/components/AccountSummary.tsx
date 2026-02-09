import { Card, Row, Col } from "react-bootstrap";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { useGetAssetsQuery, useGetAssetInfosQuery } from "../functions/api";
import { formatString } from "../functions/helperFunctions";
import StateHandler from "./StateHandler";

interface Props {
  accountId: number;
}

export default function AccountSummary({ accountId }: Props) {
  const { access } = useSelector((state: RootState) => state.user);

  const {
    data: assetsRaw,
    isLoading: assetsLoading,
    error: assetsError,
  } = useGetAssetsQuery(accountId, {
    skip: !access || !accountId,
  });

  const assets = assetsRaw ?? [];
  const assetsOwned = assets.filter((item) => !item.sellDate);
  const tickers = [...new Set(assetsOwned.map((a) => a.ticker))];

  const {
    data: assetInfosRaw,
    isLoading: assetInfoLoading,
    error: assetInfoError,
  } = useGetAssetInfosQuery(tickers, {
    skip: tickers.length === 0 || !access,
  });

  const assetInfos = assetInfosRaw ?? {};
  const isLoading = assetsLoading || assetInfoLoading;

  if (!access || assetsOwned.length === 0) {
    return null;
  }

  let totalCostBasis = 0;
  let totalCurrentValue = 0;

  for (const asset of assetsOwned) {
    totalCostBasis += asset.buyPrice;
    const info = assetInfos[asset.ticker];
    if (info) {
      totalCurrentValue += info.currentPrice * asset.shares;
    }
  }

  const totalPercentChange =
    totalCostBasis > 0
      ? (totalCurrentValue - totalCostBasis) / totalCostBasis
      : 0;

  return (
    <StateHandler
      isLoading={isLoading}
      errors={[assetsError, assetInfoError]}
      content={
        <Card className="mb-4 shadow-sm">
          <Card.Body>
            <Card.Title className="fw-bold mb-3">Account Summary</Card.Title>
            <Row>
              <Col md={4} className="mb-3 mb-md-0">
                <div className="text-muted small">Total Cost Basis</div>
                <div className="fs-5">
                  {formatString(totalCostBasis, "money")}
                </div>
              </Col>
              <Col md={4} className="mb-3 mb-md-0">
                <div className="text-muted small">Total Current Value</div>
                <div className="fs-5">
                  {formatString(totalCurrentValue, "money")}
                </div>
              </Col>
              <Col md={4}>
                <div className="text-muted small">Total Percent Change</div>
                <div
                  className="fs-5"
                  style={{
                    color:
                      totalPercentChange > 0
                        ? "var(--bs-success)"
                        : totalPercentChange < 0
                        ? "var(--bs-danger)"
                        : "inherit",
                  }}
                >
                  {formatString(totalPercentChange, "percent")}
                </div>
              </Col>
            </Row>
          </Card.Body>
        </Card>
      }
    />
  );
}

