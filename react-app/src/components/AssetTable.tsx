import { Button } from "react-bootstrap";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { useGetAssetsQuery, useGetAssetInfosQuery } from "../functions/api";
import { formatString } from "../functions/helperFunctions";
import { useNavigate } from "react-router-dom";
import { useEffect } from "react";
import { SortableTable } from "./SortableTable";

export default function AssetTable() {
  const navigate = useNavigate();
  const { access } = useSelector((state: RootState) => state.user);
  const { data: assetsRaw, refetch, isLoading: assetInfoLoading, error: assetError } = useGetAssetsQuery();
  const assets = assetsRaw ?? [];
  const tickers = [...new Set(assets.map((a) => a.ticker))];
  const {
    data: assetInfosRaw,
    isLoading: assetLoading,
    error: assetInfoError,
  } = useGetAssetInfosQuery(tickers, {
    skip: tickers.length === 0 || !access,
  });
  const assetInfos = assetInfosRaw ?? {};
  const isLoading = assetLoading || assetInfoLoading
  useEffect(() => {
    if (access) refetch();
  }, [access, refetch]);

  if (!access) return null;

  const assetsOwned = assets.filter((item) => !item.sellDate);

  if (assetsOwned.length === 0 && !isLoading) return null;

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
      <SortableTable data={data} columns={columns} initialSortKey="ticker" isLoading={isLoading} errors={[assetError, assetInfoError]}/>
    </>
  );
}