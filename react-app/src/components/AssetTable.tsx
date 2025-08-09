import { Button, Spinner, Alert } from "react-bootstrap";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { useGetAssetsQuery, useGetAssetInfosQuery } from "../functions/api";
import { formatString, getErrorMessages } from "../functions/helperFunctions";
import { useNavigate } from "react-router-dom";
import { useEffect } from "react";
import { SortableTable } from "./SortableTable";

export default function AssetTable() {
  const navigate = useNavigate();
  const { access } = useSelector((state: RootState) => state.user);
  const { data: assetsRaw, refetch, error: assetError } = useGetAssetsQuery();
  const assets = assetsRaw ?? [];
  const tickers = [...new Set(assets.map((a) => a.ticker))];
  const {
    data: assetInfosRaw,
    isLoading,
    error: assetInfoError,
  } = useGetAssetInfosQuery(tickers, {
    skip: tickers.length === 0 || !access,
  });
  const assetInfos = assetInfosRaw ?? {};

  useEffect(() => {
    if (access) refetch();
  }, [access, refetch]);

  if (!access) return null;
  if (assetError)
    return <Alert variant="danger">{getErrorMessages(assetError["data"])}</Alert>;
  if (assetInfoError)
    return (
      <Alert variant="danger">{getErrorMessages(assetInfoError["data"])}</Alert>
    );
  if (!assets || !assetInfos)
    return (
      <>
        <h3>Assets Owned</h3>
        <Spinner animation="border" />
      </>
    );

  const assetsOwned = assets.filter((item) => !item.sellDate);

  if (assetsOwned.length === 0 && !isLoading) return null;

  if (isLoading)
    return (
      <>
        <h3>Assets Owned</h3>
        <Spinner animation="border" />
      </>
    );

  const assetsByTicker: Record<string, { totalShares: number; totalCost: number }> = {};
  for (const asset of assetsOwned) {
    if (!assetsByTicker[asset.ticker]) {
      assetsByTicker[asset.ticker] = { totalShares: 0, totalCost: 0 };
    }
    assetsByTicker[asset.ticker].totalShares += asset.shares;
    assetsByTicker[asset.ticker].totalCost += asset.buyPrice;
  }

  const data = Object.entries(assetsByTicker).map(([ticker, data]) => {
    const info = assetInfos[ticker];
    return {
      ticker,
      totalShares: data.totalShares,
      averageBuyPrice: data.totalCost / data.totalShares,
      totalCost: data.totalCost,
      currentPrice: info ? info.currentPrice * data.totalShares : null,
      percentChange: info
        ? (info.currentPrice * data.totalShares - data.totalCost) / data.totalCost
        : null,
      shortName: info?.shortName ?? "Error Loading Info",
      hasError: !info,
    };
  });

  const columns = [
    {
      label: "Ticker",
      sortKey: "ticker",
    },
    {
      label: "Name",
      sortKey: "shortName",
    },
    {
      label: "Total Shares",
      sortKey: "totalShares",
      render: (row: any) => formatString(row.totalShares, "amount"),
    },
    {
      label: "Average Buy Price",
      sortKey: "averageBuyPrice",
      render: (row: any) => formatString(row.averageBuyPrice, "money"),
    },
    {
      label: "Total Buy Price",
      sortKey: "totalCost",
      render: (row: any) => formatString(row.totalCost, "money"),
    },
    {
      label: "Current Price",
      sortKey: "currentPrice",
      render: (row: any) =>
        row.currentPrice !== null ? formatString(row.currentPrice, "money") : "N/A",
    },
    {
      label: "Percent Change",
      sortKey: "percentChange",
      render: (row: any) =>
        row.percentChange !== null ? formatString(row.percentChange, "percent") : "N/A",
    },
    {
      label: "View Asset",
      sortKey: "viewAsset",
      sortable: false,
      render: (row: any) => (
        <Button onClick={() => navigate(`/asset/${row.ticker}`)}>
          {`View ${row.ticker}`}
        </Button>
      ),
    },
  ];

  return (
    <>
      <h3>Assets Owned</h3>
      <SortableTable data={data} columns={columns} initialSortKey="ticker" />
    </>
  );
}


/*
import { Button, Spinner, Alert, Table } from "react-bootstrap";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { useGetAssetsQuery, useGetAssetInfosQuery } from "../functions/api";
import { formatString, getErrorMessages } from "../functions/helperFunctions";
import { useNavigate } from "react-router-dom";
import { useEffect } from "react";
import { useState, useMemo } from "react";

function AssetRow({ asset }) {
  const navigate = useNavigate();
  if (!asset) return null;
  return (
    <tr>
      <td>{asset.ticker}</td>
      <td>{asset.shortName}</td>
      <td>{formatString(asset.totalShares, "amount")}</td>
      <td>{formatString(asset.averageBuyPrice, "money")}</td>
      <td>{formatString(asset.totalCost, "money")}</td>
      <td>{formatString(asset.currentPrice, "money")}</td>
      <td>{formatString(asset.percentChange, "percent")}</td>
      <td>
        <Button
          onClick={() => {
            navigate(`/asset/${asset.ticker}`);
          }}
        >{`View ${asset.ticker}`}</Button>
      </td>
    </tr>
  );
}

export default function AssetTable() {
  const { access } = useSelector((state: RootState) => state.user);
  const { data: assetsRaw, refetch, error: assetError } = useGetAssetsQuery();
  const assets = assetsRaw ?? [];
  const tickers = [...new Set(assets?.map((asset) => asset.ticker) ?? [])];
  const {
    data: assetInfosRaw,
    isLoading,
    error: assetInfoError,
  } = useGetAssetInfosQuery(tickers, {
    skip: tickers.length === 0 || !access,
  });
  const assetInfos = assetInfosRaw ?? {};
  useEffect(() => {
    if (access) {
      refetch();
    }
  }, [access, refetch]);

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

  const assetsOwned = assets.filter((item) => !item.sellDate);

  const assetsByTicker: Record<
    string,
    { totalShares: number; totalCost: number }
  > = {};
  for (const asset of assetsOwned) {
    if (!assetsByTicker[asset.ticker]) {
      assetsByTicker[asset.ticker] = { totalShares: 0, totalCost: 0 };
    }
    assetsByTicker[asset.ticker].totalShares += asset.shares;
    assetsByTicker[asset.ticker].totalCost += asset.buyPrice;
  }

  const data = Object.entries(assetsByTicker).map(([ticker, data]) => {
    const info = assetInfos[ticker];
    return {
      ticker,
      totalShares: data.totalShares,
      averageBuyPrice: data.totalCost / data.totalShares,
      totalCost: data.totalCost,
      currentPrice: info ? info.currentPrice * data.totalShares : null,
      percentChange: info
        ? (info.currentPrice * data.totalShares) / data.totalCost
        : null,
      shortName: info?.shortName ?? "Error Loading Info",
      hasError: !info, // easy flag for UI
    };
  });

  const sortedData = useMemo(() => {
    if (!data) return [];
    return [...data].sort((a, b) => {
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

  if (!access) {
    return <></>;
  }
  if (assetError)
    return (
      <Alert variant="danger">{getErrorMessages(assetError["data"])}</Alert>
    );
  if (assetInfoError)
    return (
      <Alert variant="danger">{getErrorMessages(assetInfoError["data"])}</Alert>
    );
  if (!assets || !assetInfos)
    return (
      <>
        <h3>Assets Owned</h3>
        <Spinner animation="border" />
      </>
    );

  if (assetsOwned.length == 0 && access && !isLoading) {
    return null;
  }

  if (isLoading)
    return (
      <>
        <h3>Assets Owned</h3>
        <Spinner animation="border" />
      </>
    );
  const columns = [
    { label: "Ticker", sortKey: "ticker" },
    { label: "Name", sortKey: "shortName" },
    { label: "Total Shares", sortKey: "totalShares" },
    { label: "Average Buy Price", sortKey: "averageBuyPrice" },
    { label: "Total Buy Price", sortKey: "totalCost" },
    { label: "Current Price", sortKey: "currentPrice" },
    { label: "Percent Change", sortKey: "percentChange" },
    { label: "View Asset", sortKey: "viewAsset" },
  ];
  return (
    <>
      <h3>Assets Owned</h3>
      <Table>
        <thead>
          <tr>
            {columns.map(({ label, sortKey }) => (
              <th
                key={label}
                onClick={() => onSort(sortKey)}
                style={{ cursor: "pointer" }}
              >
                {label} {renderSortArrow(sortKey)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {sortedData.map((asset) => (
            <AssetRow key={asset.ticker} asset={asset} />
          ))}
        </tbody>
      </Table>
    </>
  );
}
*/