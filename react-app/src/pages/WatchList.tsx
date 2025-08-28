import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from "../main";
import WatchListTable from "../components/WatchListTable";
import { useEffect } from "react";
import { loadTickers } from "../reducers/watchListReducer";
import BenchmarkTable from "../components/BenchmarkTable";
import WatchListForm from "../components/WatchListForm";
import FinnhubBanner from "../components/FinnhubBanner";
import { Alert } from "react-bootstrap";
import TickerSearchForm from "../components/TickerSearchForm";

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
      <h3>Watchlist</h3>
      <WatchListTable />
      {error && <Alert variant="danger">{error}</Alert>}
      <WatchListForm />
      <TickerSearchForm />
      <FinnhubBanner />
    </>
  );
}