import BenchmarkCompareTable from "../components/BenchmarkCompareTable";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import LoadingModal from "../components/LoadingModal";
import AssetPieChart from "../components/AssetPieChart";
import PortfolioOverview from "../components/PortfolioOverview";
import { useGetAssetsQuery, useGetAssetInfosQuery } from "../functions/api";
import LoginRequired from "../components/LoginRequired";

export default function Visualizer() {
  const { access } = useSelector((state: RootState) => state.user);

  // Fetch initial assets
  const { data: assetsRaw, isLoading: assetsLoading } = useGetAssetsQuery(
    undefined,
    { skip: !access },
  );
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
      <LoginRequired
        title="Portfolio Insights"
        message="Please sign in to visualize your portfolio performance and allocation."
        buttonText="Sign In to View"
      />
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
