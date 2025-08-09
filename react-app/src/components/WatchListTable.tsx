import { formatString, getErrorMessages } from "../functions/helperFunctions";
import { IEquityInfo, IETFInfo } from "../interfaces";
import { useGetAssetInfosQuery } from "../functions/api";
import { Button, Spinner, Alert } from "react-bootstrap";
import { useSelector, useDispatch } from "react-redux";
import { AppDispatch, RootState } from "../main";
import { removeTicker } from "../reducers/watchListReducer";
import { useNavigate } from "react-router-dom";
import { SortableTable } from "./SortableTable";

export default function WatchListTable() {
  const tickers = useSelector((state: RootState) => state.watchList.tickers);
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();

  const { data, error } = useGetAssetInfosQuery(tickers, {
    skip: tickers.length === 0,
  });

  if (error)
    return <Alert variant="danger">{getErrorMessages(error["data"])}</Alert>;

  if (!data) return <Spinner animation="border" />;

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

  return <SortableTable data={dataArr} columns={columns} initialSortKey="ticker" />;
}

/*
import { formatString, getErrorMessages } from "../functions/helperFunctions";
import { IEquityInfo, IETFInfo } from "../interfaces";
import { useGetAssetInfosQuery } from "../functions/api";
import { Button, Spinner, Table, Alert } from "react-bootstrap";
import { useSelector, useDispatch } from "react-redux";
import { AppDispatch, RootState } from "../main";
import { removeTicker } from "../reducers/watchListReducer";
import { useNavigate } from "react-router-dom";
import { useState, useMemo } from "react";

function WatchListRow({ assetInfo }) {
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();
  return (
    <tr>
      <td>{formatString(assetInfo.ticker, "text")}</td>
      <td>{formatString(assetInfo.shortName, "text")}</td>
      <td>{assetInfo.type}</td>
      <td>{formatString(assetInfo.currentPrice, "money")}</td>
      <td>{formatString(assetInfo.percentChangeDaily, "percent")}</td>
      <td>{formatString(assetInfo.percentChangeWeekly, "percent")}</td>
      <td>{formatString(assetInfo.percentChangeMonthly, "percent")}</td>
      <td>{formatString(assetInfo.percentChangeYTD, "percent")}</td>
      <td>{formatString(assetInfo.percentChangeYearly, "percent")}</td>
      <td>{formatString(assetInfo.percentChange3Years, "percent")}</td>
      <td>{formatString(assetInfo.percentChange5Years, "percent")}</td>
      <td>
        <Button onClick={() => dispatch(removeTicker(assetInfo.ticker))}>
          {`Remove ${assetInfo.ticker}`}
        </Button>
      </td>
      <td>
        <Button onClick={() => navigate(`/asset/${assetInfo.ticker}`)}>
          {`View ${assetInfo.ticker}`}
        </Button>
      </td>
    </tr>
  );
}

export default function WatchListTable() {
  const tickers = useSelector((state: RootState) => state.watchList.tickers);
  const { data, error } = useGetAssetInfosQuery(tickers, {
    skip: tickers.length === 0,
  });

  const [sortConfig, setSortConfig] = useState<{
    key: string;
    direction: "asc" | "desc";
  }>({
    key: "ticker",
    direction: "asc",
  });

  const onSort = (key: string) => {
    setSortConfig((prev) => ({
      key,
      direction: prev.key === key && prev.direction === "asc" ? "desc" : "asc",
    }));
  };

  const renderSortArrow = (key: string) => {
    if (sortConfig.key !== key) return null;
    return sortConfig.direction === "asc" ? "▲" : "▼";
  };

  const sortedData = useMemo(() => {
    if (!data) return [];
    const arr = Object.values(data as Record<string, IEquityInfo | IETFInfo>);
    return [...arr].sort((a, b) => {
      const aValue = a[sortConfig.key as keyof typeof a];
      const bValue = b[sortConfig.key as keyof typeof b];
      if (aValue == null) return 1;
      if (bValue == null) return -1;
      if (typeof aValue === "string") {
        return sortConfig.direction === "asc"
          ? aValue.localeCompare(bValue as string)
          : (bValue as string).localeCompare(aValue);
      }
      if (typeof aValue === "number") {
        return sortConfig.direction === "asc"
          ? (aValue as number) - (bValue as number)
          : (bValue as number) - (aValue as number);
      }
      return 0;
    });
  }, [data, sortConfig]);

  if (error)
    return <Alert variant="danger">{getErrorMessages(error["data"])}</Alert>;

  if (!data) return <Spinner animation="border" />;

  return (
    <Table>
      <thead>
        <tr>
          <th onClick={() => onSort("ticker")}>
            Ticker {renderSortArrow("ticker")}
          </th>
          <th onClick={() => onSort("shortName")}>
            Name {renderSortArrow("shortName")}
          </th>
          <th onClick={() => onSort("type")}>Type {renderSortArrow("type")}</th>
          <th onClick={() => onSort("currentPrice")}>
            Price {renderSortArrow("currentPrice")}
          </th>
          <th onClick={() => onSort("percentChangeDaily")}>
            Percent Change Today {renderSortArrow("percentChangeDaily")}
          </th>
          <th onClick={() => onSort("percentChangeWeekly")}>
            Percent Change Weekly {renderSortArrow("percentChangeWeekly")}
          </th>
          <th onClick={() => onSort("percentChangeMonthly")}>
            Percent Change Monthly {renderSortArrow("percentChangeMonthly")}
          </th>
          <th onClick={() => onSort("percentChangeYTD")}>
            Percent Change YTD {renderSortArrow("percentChangeYTD")}
          </th>
          <th onClick={() => onSort("percentChangeYearly")}>
            Percent Change 1 Year {renderSortArrow("percentChangeYearly")}
          </th>
          <th onClick={() => onSort("percentChange3Years")}>
            Percent Change 3 Years {renderSortArrow("percentChange3Years")}
          </th>
          <th onClick={() => onSort("percentChange5Years")}>
            Percent Change 5 Years {renderSortArrow("percentChange5Years")}
          </th>
          <th>Remove</th>
          <th>View Asset</th>
        </tr>
      </thead>
      <tbody>
        {sortedData.map((assetInfo) => (
          <WatchListRow key={assetInfo.ticker} assetInfo={assetInfo} />
        ))}
      </tbody>
    </Table>
  );
}
*/