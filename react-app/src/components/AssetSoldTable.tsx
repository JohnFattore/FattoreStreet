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
        <h3>Assets Sold</h3>
        <Spinner animation="border" />
      </>
    );

  const assetsSold = assets.filter((item) => item.sellDate);

  if (assetsSold.length === 0 && !isLoading) return null;

  if (isLoading)
    return (
      <>
        <h3>Assets Sold</h3>
        <Spinner animation="border" />
      </>
    );

  const assetsByTicker: Record<string, { totalShares: number; totalCost: number, totalSellPrice: number }> = {};
  for (const asset of assetsSold) {
    if (!assetsByTicker[asset.ticker]) {
      assetsByTicker[asset.ticker] = { totalShares: 0, totalCost: 0, totalSellPrice: 0 };
    }
    if (!asset.sellPrice) {
      throw Error(`Sell price for ${asset.ticker} is null`)
    }
    assetsByTicker[asset.ticker].totalShares += asset.shares;
    assetsByTicker[asset.ticker].totalCost += asset.buyPrice;
    assetsByTicker[asset.ticker].totalSellPrice += asset.sellPrice;
  }

  const data = Object.entries(assetsByTicker).map(([ticker, data]) => {
    const info = assetInfos[ticker];
    return {
      ticker,
      totalShares: data.totalShares,
      averageBuyPrice: data.totalCost / data.totalShares,
      totalCost: data.totalCost,
      totalSalePrice: data.totalSellPrice,
      percentChange: (data.totalSellPrice - data.totalCost) / data.totalCost,
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
      label: "Total Sell Price",
      sortKey: "totalSalePrice",
      render: (row: any) => formatString(row.totalSalePrice, "money"),
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
      <h3>Assets Sold</h3>
      <SortableTable data={data} columns={columns} initialSortKey="ticker" />
    </>
  );
}