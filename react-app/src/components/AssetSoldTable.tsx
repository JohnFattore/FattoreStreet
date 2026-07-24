import { Button } from "react-bootstrap";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { useGetAssetsQuery, useGetAssetInfosQuery } from "../functions/api";
import { formatString } from "../functions/helperFunctions";
import { useNavigate } from "react-router-dom";
import { useEffect } from "react";
import { SortableTable } from "./SortableTable";

type AssetSoldSummaryRow = {
  ticker: string;
  totalShares: number;
  averageBuyPrice: number;
  totalCost: number;
  totalSalePrice: number;
  percentChange: number;
  shortName: string;
  hasError: boolean;
};

export default function AssetTable() {
  const navigate = useNavigate();
  const { access } = useSelector((state: RootState) => state.user);
  const {
    data: assetsRaw,
    refetch,
    isLoading: assetInfoLoading,
    error: assetError,
  } = useGetAssetsQuery();
  const assets = assetsRaw ?? [];
  const tickers = [...new Set(assets.map((a) => a.ticker))];
  const { data: assetInfosRaw, isLoading: assetLoading } =
    useGetAssetInfosQuery(tickers, {
      skip: tickers.length === 0 || !access,
    });
  const assetInfos = assetInfosRaw ?? {};
  const isLoading = assetLoading || assetInfoLoading;
  useEffect(() => {
    if (access) refetch();
  }, [access, refetch]);

  if (!access) return null;

  const assetsSold = assets.filter((item) => item.sellDate);

  if (assetsSold.length === 0 && !isLoading) return null;

  const assetsByTicker: Record<
    string,
    { totalShares: number; totalCost: number; totalSellPrice: number }
  > = {};
  for (const asset of assetsSold) {
    if (!assetsByTicker[asset.ticker]) {
      assetsByTicker[asset.ticker] = {
        totalShares: 0,
        totalCost: 0,
        totalSellPrice: 0,
      };
    }
    if (!asset.sellPrice) {
      throw Error(`Sell price for ${asset.ticker} is null`);
    }
    assetsByTicker[asset.ticker].totalShares += asset.shares;
    assetsByTicker[asset.ticker].totalCost += asset.buyPrice;
    assetsByTicker[asset.ticker].totalSellPrice += asset.sellPrice;
  }

  const data: AssetSoldSummaryRow[] = Object.entries(assetsByTicker).map(
    ([ticker, data]) => {
      const info = assetInfos[ticker];
      return {
        ticker,
        totalShares: data.totalShares,
        averageBuyPrice: data.totalCost / data.totalShares,
        totalCost: data.totalCost,
        totalSalePrice: data.totalSellPrice,
        percentChange: (data.totalSellPrice - data.totalCost) / data.totalCost,
        shortName: assetLoading ? "Loading..." : (info?.shortName ?? "N/A"),
        hasError: !info && !assetLoading,
      };
    },
  );

  const columns = [
    {
      label: "Ticker",
      sortKey: "ticker",
      render: (row: AssetSoldSummaryRow) => (
        <div onClick={() => navigate(`/asset/${row.ticker}`)}>{row.ticker}</div>
      ),
    },
    {
      label: "Name",
      sortKey: "shortName",
    },
    {
      label: "Total Shares",
      sortKey: "totalShares",
      render: (row: AssetSoldSummaryRow) =>
        formatString(row.totalShares, "amount"),
    },
    {
      label: "Average Buy Price",
      sortKey: "averageBuyPrice",
      render: (row: AssetSoldSummaryRow) =>
        formatString(row.averageBuyPrice, "money"),
    },
    {
      label: "Total Buy Price",
      sortKey: "totalCost",
      render: (row: AssetSoldSummaryRow) =>
        formatString(row.totalCost, "money"),
    },
    {
      label: "Total Sell Price",
      sortKey: "totalSalePrice",
      render: (row: AssetSoldSummaryRow) =>
        formatString(row.totalSalePrice, "money"),
    },
    {
      label: "Percent Change",
      sortKey: "percentChange",
      render: (row: AssetSoldSummaryRow) =>
        row.percentChange !== null
          ? formatString(row.percentChange, "percent")
          : "N/A",
    },
    {
      label: "View Asset",
      sortKey: "viewAsset",
      sortable: false,
      render: (row: AssetSoldSummaryRow) => (
        <Button onClick={() => navigate(`/asset/${row.ticker}`)}>
          {`View ${row.ticker}`}
        </Button>
      ),
    },
  ];

  return (
    <>
      <h3>Assets Sold</h3>
      <SortableTable
        data={data}
        columns={columns}
        initialSortKey="ticker"
        isLoading={assetInfoLoading}
        errors={[assetError]}
      />
    </>
  );
}
