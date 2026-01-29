import BenchmarkCompareTable from "../components/BenchmarkCompareTable";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import LoginForm from "../components/LoginForm";
import AssetPieChart from "../components/AssetPieChart";
import PortfolioOverview from "../components/PortfolioOverview";
import { Alert } from "react-bootstrap";

export default function Visualizer() {
  const { access } = useSelector((state: RootState) => state.user);
  if (!access) {
    return (
      <>
        <Alert variant="info">Please login to view your portfolio.</Alert>
        <LoginForm />
      </>
    );
  }
  return (
    <>
      <PortfolioOverview />
      <AssetPieChart />
      <BenchmarkCompareTable />
    </>
  );
}