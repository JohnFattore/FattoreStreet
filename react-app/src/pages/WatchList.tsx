import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from "../main";
import EquityWatchListTable from "../components/EquityWatchListTable";
import ETFWatchListTable from "../components/ETFWatchListTable";
import { useEffect } from "react";
import { loadTickers } from "../reducers/watchListReducer";
import BenchmarkTable from "../components/BenchmarkTable";
import WatchListForm from "../components/WatchListForm";
import FinnhubBanner from "../components/FinnhubBanner";
import { translateError } from "../functions/helperFunctions";
import { Alert } from "react-bootstrap";

export default function WatchList() {
  const dispatch = useDispatch<AppDispatch>();
  const { error } = useSelector((state: RootState) => state.watchList);
  useEffect(() => {
    dispatch(loadTickers());
  }, [dispatch]);

  return (
    <>
      <h3>Common Benchmarks</h3>
      <BenchmarkTable />
      <h3>Stock Watchlist</h3>
      <EquityWatchListTable />
      <h3>ETF Watchlist</h3>
      <ETFWatchListTable />
      {error && <Alert variant="danger">{translateError(error)}</Alert>}
      <WatchListForm />
      <FinnhubBanner />
    </>
  );
}