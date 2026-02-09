import { useState } from "react";
import BenchmarkCompareTable from "../components/BenchmarkCompareTable";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import LoginModal from "../components/LoginModal";
import LoadingModal from "../components/LoadingModal";
import AssetPieChart from "../components/AssetPieChart";
import PortfolioOverview from "../components/PortfolioOverview";
import { useGetAssetsQuery, useGetAssetInfosQuery } from "../functions/api";
import { Alert, Container, Row, Col, Button } from "react-bootstrap";

export default function Visualizer() {
  const { access } = useSelector((state: RootState) => state.user);
  const [showLogin, setShowLogin] = useState(true);

  // Fetch initial assets
  const { data: assetsRaw, isLoading: assetsLoading } = useGetAssetsQuery(undefined, { skip: !access });
  const assets = assetsRaw ?? [];

  // Derived tickers to trigger asset-info fetch
  const tickers = [...new Set(assets.map((a) => a.ticker))];

  // Fetch asset details
  const { isLoading: assetInfoLoading } = useGetAssetInfosQuery(tickers, {
    skip: tickers.length === 0 || !access,
  });

  const isLoading = assetsLoading || assetInfoLoading;

  if (!access) {
    return (
      <Container className="mt-5 text-center">
        <Row className="justify-content-center">
          <Col md={8}>
            <Alert variant="info" className="p-5 shadow-sm">
              <h2 className="mb-4">Portfolio Insights</h2>
              <p className="lead mb-4">Please sign in to visualize your portfolio performance and allocation.</p>
              <Button variant="primary" size="lg" onClick={() => setShowLogin(true)}>
                Sign In to View
              </Button>
            </Alert>
          </Col>
        </Row>
        <LoginModal show={showLogin} onHide={() => setShowLogin(false)} />
      </Container>
    );
  }

  return (
    <>
      <LoadingModal show={isLoading} />
      {!isLoading && (
        <>
          <PortfolioOverview />
          <AssetPieChart />
          <BenchmarkCompareTable />
        </>
      )}
    </>
  );
}