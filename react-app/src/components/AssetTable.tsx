import { Button } from "react-bootstrap";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { useGetAssetsQuery, useGetAssetInfosQuery } from "../functions/api";
import { formatString } from "../functions/helperFunctions";
import { useNavigate } from "react-router-dom";
import { useEffect } from "react";
import { SortableTable } from "./SortableTable";

type AssetSummaryRow = {
  ticker: string;
  totalShares: number;
  averageBuyPrice: number;
  totalCost: number;
  currentPrice: number | null;
  percentChange: number | null;
  shortName: string;
  hasError: boolean;
};

interface Props {
  accountId?: number;
}

export default function AssetTable({ accountId }: Props) {
  const navigate = useNavigate();
  const { access } = useSelector((state: RootState) => state.user);
  const {
    data: assetsRaw,
    refetch,
    isLoading: assetInfoLoading,
    error: assetError,
  } = useGetAssetsQuery(accountId);
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

  const assetsOwned = assets.filter((item) => !item.sellDate);

  if (assetsOwned.length === 0 && !isLoading) return null;

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

  const data: AssetSummaryRow[] = Object.entries(assetsByTicker).map(
    ([ticker, data]) => {
      const info = assetInfos[ticker];
      return {
        ticker,
        totalShares: data.totalShares,
        averageBuyPrice: data.totalCost / data.totalShares,
        totalCost: data.totalCost,
        currentPrice: info ? info.currentPrice * data.totalShares : null,
        percentChange: info
          ? (info.currentPrice * data.totalShares - data.totalCost) /
            data.totalCost
          : null,
        shortName: assetLoading ? "Loading..." : (info?.shortName ?? "N/A"),
        hasError: !info && !assetLoading,
      };
    },
  );

  const columns = [
    {
      label: "Ticker",
      sortKey: "ticker",
      render: (row: AssetSummaryRow) => (
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
      render: (row: AssetSummaryRow) => formatString(row.totalShares, "amount"),
    },
    {
      label: "Average Buy Price",
      sortKey: "averageBuyPrice",
      render: (row: AssetSummaryRow) =>
        formatString(row.averageBuyPrice, "money"),
    },
    {
      label: "Total Buy Price",
      sortKey: "totalCost",
      render: (row: AssetSummaryRow) => formatString(row.totalCost, "money"),
    },
    {
      label: "Current Price",
      sortKey: "currentPrice",
      render: (row: AssetSummaryRow) =>
        assetLoading
          ? "Loading..."
          : row.currentPrice !== null
            ? formatString(row.currentPrice, "money")
            : "N/A",
    },
    {
      label: "Percent Change",
      sortKey: "percentChange",
      render: (row: AssetSummaryRow) =>
        assetLoading
          ? "Loading..."
          : row.percentChange !== null
            ? formatString(row.percentChange, "percent")
            : "N/A",
    },
    {
      label: "View Asset",
      sortKey: "viewAsset",
      sortable: false,
      render: (row: AssetSummaryRow) => (
        <Button onClick={() => navigate(`/asset/${row.ticker}`)}>
          {`View ${row.ticker}`}
        </Button>
      ),
    },
  ];

  return (
    <>
      <h3>Assets Owned</h3>
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
