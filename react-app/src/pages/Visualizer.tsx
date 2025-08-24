import BenchmarkCompareTable from "../components/BenchmarkCompareTable";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import LoginForm from "../components/LoginForm";
import AssetPieChart from "../components/AssetPieChart";
import PortfolioOverview from "../components/PortfolioOverview";

export default function Visualizer() {
  const { access } = useSelector((state: RootState) => state.user);
  if (!access) {
    return <LoginForm />;
  }
  return (
    <>
      <PortfolioOverview />
      <AssetPieChart />
      <BenchmarkCompareTable />
    </>
  );
}