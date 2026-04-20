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
import { useGetAssetInfosQuery } from "../functions/api";

export default function WatchList() {
  const dispatch = useDispatch<AppDispatch>();
  const { error, tickers } = useSelector((state: RootState) => state.watchList);
  useEffect(() => {
    dispatch(loadTickers());
  }, [dispatch]);

  const {
    data: assetInfos,
    isLoading: assetInfosLoading,
    error: assetInfosError,
  } = useGetAssetInfosQuery(tickers, {
    skip: tickers.length === 0,
  });

  return (
    <>
      <h3>Common Benchmarks</h3>
      <BenchmarkTable />
      <h3>Watchlist</h3>
      <WatchListTable
        tickers={tickers}
        assetInfos={assetInfos}
        assetInfosLoading={assetInfosLoading}
        assetInfosError={assetInfosError}
      />
      {error && <Alert variant="danger">{error}</Alert>}
      <WatchListForm assetInfosLoading={assetInfosLoading} />
      <TickerSearchForm />
      <FinnhubBanner />
    </>
  );
}