import { formatString } from "../functions/helperFunctions";
import { IEquityInfo, IETFInfo } from "../interfaces";
import { useGetAssetInfosQuery } from "../functions/api";
import { Button } from "react-bootstrap";
import { useSelector, useDispatch } from "react-redux";
import { AppDispatch, RootState } from "../main";
import { removeTicker } from "../reducers/watchListReducer";
import { useNavigate } from "react-router-dom";
import { SortableTable } from "./SortableTable";

export default function WatchListTable() {
  const tickers = useSelector((state: RootState) => state.watchList.tickers);
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();

  const { data: dataRaw, isLoading, error } = useGetAssetInfosQuery(tickers, {
    skip: tickers.length === 0,
  });

  const data = dataRaw ?? []

  const dataArr = Object.values(data as Record<string, IEquityInfo | IETFInfo>);

  const columns = [
    {
      label: "Ticker",
      sortKey: "ticker",
      render: (row: any) => formatString(row.ticker, "text"),
    },
    {
      label: "Name",
      sortKey: "shortName",
      render: (row: any) => formatString(row.shortName, "text"),
    },
    { label: "Type", sortKey: "type" },
    {
      label: "Price",
      sortKey: "currentPrice",
      render: (row: any) => formatString(row.currentPrice, "money"),
    },
    {
      label: "Percent Change Today",
      sortKey: "percentChangeDaily",
      render: (row: any) => formatString(row.percentChangeDaily, "percent"),
    },
    {
      label: "Percent Change Weekly",
      sortKey: "percentChangeWeekly",
      render: (row: any) => formatString(row.percentChangeWeekly, "percent"),
    },
    {
      label: "Percent Change Monthly",
      sortKey: "percentChangeMonthly",
      render: (row: any) => formatString(row.percentChangeMonthly, "percent"),
    },
    {
      label: "Percent Change YTD",
      sortKey: "percentChangeYTD",
      render: (row: any) => formatString(row.percentChangeYTD, "percent"),
    },
    {
      label: "Percent Change 1 Year",
      sortKey: "percentChangeYearly",
      render: (row: any) => formatString(row.percentChangeYearly, "percent"),
    },
    {
      label: "Percent Change 3 Years",
      sortKey: "percentChange3Years",
      render: (row: any) => formatString(row.percentChange3Years, "percent"),
    },
    {
      label: "Percent Change 5 Years",
      sortKey: "percentChange5Years",
      render: (row: any) => formatString(row.percentChange5Years, "percent"),
    },
    {
      label: "Remove",
      sortKey: "remove",
      sortable: false,
      render: (row: any) => (
        <Button onClick={() => dispatch(removeTicker(row.ticker))}>
          {`Remove ${row.ticker}`}
        </Button>
      ),
    },
    {
      label: "View Asset",
      sortKey: "view",
      sortable: false,
      render: (row: any) => (
        <Button onClick={() => navigate(`/asset/${row.ticker}`)}>
          {`View ${row.ticker}`}
        </Button>
      ),
    },
  ];

  return <SortableTable data={dataArr} columns={columns} initialSortKey="ticker" isLoading={isLoading} errors={[error]}/>;
}