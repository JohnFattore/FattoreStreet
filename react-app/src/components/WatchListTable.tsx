import { useState } from "react";
import { formatString } from "../functions/helperFunctions";
import { IEquityInfo, IETFInfo, ISECData } from "../interfaces";
import { useGetAssetInfosQuery, useGetSecEdgarDataBatchQuery } from "../functions/api";
import { Button, ButtonGroup } from "react-bootstrap";
import { useSelector, useDispatch } from "react-redux";
import { AppDispatch, RootState } from "../main";
import { removeTicker } from "../reducers/watchListReducer";
import { useNavigate } from "react-router-dom";
import { SortableTable } from "./SortableTable";

type View = "performance" | "edgar";

export default function WatchListTable() {
  const [view, setView] = useState<View>("performance");
  const tickers = useSelector((state: RootState) => state.watchList.tickers);
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();

  const { data: dataRaw, isLoading, error } = useGetAssetInfosQuery(tickers, {
    skip: tickers.length === 0,
  });

  const {
    data: edgarData,
    isLoading: edgarLoading,
    error: edgarError,
  } = useGetSecEdgarDataBatchQuery(tickers, {
    skip: tickers.length === 0 || view !== "edgar",
  });

  const data = dataRaw ?? [];
  const dataArr = Object.values(data as Record<string, IEquityInfo | IETFInfo>);

  const actionColumns = [
    {
      label: "Remove",
      sortKey: "remove",
      sortable: false,
      render: (row: any) => (
        <Button size="sm" onClick={() => dispatch(removeTicker(row.ticker))}>
          Remove
        </Button>
      ),
    },
    {
      label: "View",
      sortKey: "view",
      sortable: false,
      render: (row: any) => (
        <Button size="sm" onClick={() => navigate(`/asset/${row.ticker}`)}>
          View
        </Button>
      ),
    },
  ];

  const performanceColumns = [
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
    ...actionColumns,
  ];

  const edgarColumns = [
    {
      label: "Ticker",
      sortKey: "ticker",
      render: (row: ISECData) => row.ticker,
    },
    {
      label: "TTM Revenue",
      sortKey: "ttmRevenue",
      render: (row: ISECData) => formatString(Number(row.ttmRevenue), "money"),
    },
    {
      label: "TTM Net Income",
      sortKey: "ttmNetIncome",
      render: (row: ISECData) => formatString(Number(row.ttmNetIncome), "money"),
    },
    {
      label: "Revenue YoY",
      sortKey: "ttmRevenueYoY",
      render: (row: ISECData) => row.ttmRevenueYoY,
    },
    {
      label: "Net Income YoY",
      sortKey: "ttmNetIncomeYoY",
      render: (row: ISECData) => row.ttmNetIncomeYoY,
    },
    {
      label: "Net Margin",
      sortKey: "netMargin",
      render: (row: ISECData) => row.netMargin,
    },
    {
      label: "Gross Margin",
      sortKey: "grossMargin",
      render: (row: ISECData) => row.grossMargin,
    },
    {
      label: "ROA",
      sortKey: "roA",
      render: (row: ISECData) => row.roA,
    },
    {
      label: "Debt / Assets",
      sortKey: "debtToAssets",
      render: (row: ISECData) => row.debtToAssets,
    },
    {
      label: "Latest EPS",
      sortKey: "latestEps",
      render: (row: ISECData) => row.latestEps,
    },
    {
      label: "Latest Quarter",
      sortKey: "latestQuarterEnd",
      render: (row: ISECData) => row.latestQuarterEnd,
    },
    ...actionColumns,
  ];

  const isEdgar = view === "edgar";

  return (
    <div className="watchlist-table">
      <ButtonGroup>
        <Button
          variant={!isEdgar ? "primary" : "outline-primary"}
          onClick={() => setView("performance")}
        >
          Performance
        </Button>
        <Button
          variant={isEdgar ? "primary" : "outline-primary"}
          onClick={() => setView("edgar")}
        >
          SEC Edgar
        </Button>
      </ButtonGroup>

      <SortableTable
        data={isEdgar ? (edgarData || []) : dataArr}
        columns={isEdgar ? edgarColumns : performanceColumns}
        initialSortKey="ticker"
        isLoading={isEdgar ? edgarLoading : isLoading}
        errors={[isEdgar ? edgarError : error]}
      />
    </div>
  );
}
