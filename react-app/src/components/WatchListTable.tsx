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

  type PerfRow = IEquityInfo | IETFInfo;

  const performanceActionColumns = [
    {
      label: "Remove",
      sortKey: "remove",
      sortable: false,
      render: (row: PerfRow) => (
        <Button size="sm" onClick={() => dispatch(removeTicker(row.ticker))}>
          Remove
        </Button>
      ),
    },
    {
      label: "View",
      sortKey: "view",
      sortable: false,
      render: (row: PerfRow) => (
        <Button size="sm" onClick={() => navigate(`/asset/${row.ticker}`)}>
          View
        </Button>
      ),
    },
  ];

  const edgarActionColumns = [
    {
      label: "Remove",
      sortKey: "remove",
      sortable: false,
      render: (row: ISECData) => (
        <Button size="sm" onClick={() => dispatch(removeTicker(row.ticker))}>
          Remove
        </Button>
      ),
    },
    {
      label: "View",
      sortKey: "view",
      sortable: false,
      render: (row: ISECData) => (
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
      render: (row: PerfRow) => formatString(row.ticker, "text"),
    },
    {
      label: "Name",
      sortKey: "shortName",
      render: (row: PerfRow) => formatString(row.shortName, "text"),
    },
    { label: "Type", sortKey: "type" },
    {
      label: "Price",
      sortKey: "currentPrice",
      render: (row: PerfRow) => formatString(row.currentPrice, "money"),
    },
    {
      label: "Percent Change Today",
      sortKey: "percentChangeDaily",
      render: (row: PerfRow) => formatString(row.percentChangeDaily, "percent"),
    },
    {
      label: "Percent Change Weekly",
      sortKey: "percentChangeWeekly",
      render: (row: PerfRow) => formatString(row.percentChangeWeekly, "percent"),
    },
    {
      label: "Percent Change Monthly",
      sortKey: "percentChangeMonthly",
      render: (row: PerfRow) => formatString(row.percentChangeMonthly, "percent"),
    },
    {
      label: "Percent Change YTD",
      sortKey: "percentChangeYTD",
      render: (row: PerfRow) => formatString(row.percentChangeYTD, "percent"),
    },
    {
      label: "Percent Change 1 Year",
      sortKey: "percentChangeYearly",
      render: (row: PerfRow) => formatString(row.percentChangeYearly, "percent"),
    },
    {
      label: "Percent Change 3 Years",
      sortKey: "percentChange3Years",
      render: (row: PerfRow) => formatString(row.percentChange3Years, "percent"),
    },
    {
      label: "Percent Change 5 Years",
      sortKey: "percentChange5Years",
      render: (row: PerfRow) => formatString(row.percentChange5Years, "percent"),
    },
    ...performanceActionColumns,
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
      render: (row: ISECData) => formatString(row.ttmRevenue, "money"),
    },
    {
      label: "TTM Net Income",
      sortKey: "ttmNetIncome",
      render: (row: ISECData) => formatString(row.ttmNetIncome, "money"),
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
      label: "Assets",
      sortKey: "latestAssets",
      render: (row: ISECData) => formatString(row.latestAssets, "money"),
    },
    {
      label: "Liabilities",
      sortKey: "latestLiabilities",
      render: (row: ISECData) => formatString(row.latestLiabilities, "money"),
    },
    {
      label: "Equity",
      sortKey: "latestEquity",
      render: (row: ISECData) => formatString(row.latestEquity, "money"),
    },
    {
      label: "Latest Quarter",
      sortKey: "latestQuarterEnd",
      render: (row: ISECData) => row.latestQuarterEnd,
    },
    ...edgarActionColumns,
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

      {isEdgar ? (
        <SortableTable<ISECData>
          data={edgarData || []}
          columns={edgarColumns}
          initialSortKey="ticker"
          isLoading={edgarLoading}
          errors={[edgarError]}
        />
      ) : (
        <SortableTable<PerfRow>
          data={dataArr}
          columns={performanceColumns}
          initialSortKey="ticker"
          isLoading={isLoading}
          errors={[error]}
        />
      )}
    </div>
  );
}
