import { Button } from "react-bootstrap";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { useGetAssetsQuery, useGetAssetInfosQuery, useGetQuoteQuery } from "../functions/api";
import { formatString } from "../functions/helperFunctions";
import { useNavigate } from "react-router-dom";
import { useEffect } from "react";
import { SortableTable } from "./SortableTable";

export default function BenchmarkCompareTable() {
  const navigate = useNavigate();
  const { access } = useSelector((state: RootState) => state.user);
  const {
    data: assetsRaw,
    refetch,
    error: assetError,
    isLoading: assetLoading,
  } = useGetAssetsQuery();
  const assets = assetsRaw ?? [];
  const tickers = [...new Set(assets.map((a) => a.ticker))];
  const {
    data: assetInfosRaw,
    isLoading: assetInfoLoading,
    error: assetInfoError,
  } = useGetAssetInfosQuery(tickers, {
    skip: tickers.length === 0 || !access,
  });
  const assetInfos = assetInfosRaw ?? {};

  const { data: quote } = useGetQuoteQuery("SPY")
  useEffect(() => {
    if (access) refetch();
  }, [access, refetch]);

  if (!access) return null;

  const data = assets.map((asset) => {
    const info = assetInfos[asset.ticker];
    return {
      ticker: asset.ticker,
      shares: asset.shares,
      buyDate: asset.buyDate,
      buyPrice: asset.buyPrice,
      sellDate: asset.sellDate ? asset.sellDate : "Not Sold",
      sellCurrentPrice: asset.sellPrice ? asset.sellPrice : info?.currentPrice * asset.shares,
      change: asset.sellPrice
        ? (asset.sellPrice - asset.buyPrice) / asset.buyPrice
        : ((info?.currentPrice * asset.shares) - asset.buyPrice) / asset.buyPrice,
      snp500Change: asset.snp500PriceSell ? (asset.snp500PriceSell - asset.snp500PriceBuy) / asset.snp500PriceBuy : (quote?.price - asset.snp500PriceBuy) / asset.snp500PriceBuy,
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
      label: "Shares",
      sortKey: "shares",
      render: (row: any) => formatString(row.shares, "amount"),
    },
    {
      label: "Buy Date",
      sortKey: "buyDate",
      render: (row: any) => formatString(row.buyDate, "date"),
    },
    {
      label: "Buy Price",
      sortKey: "buyPrice",
      render: (row: any) => formatString(row.buyPrice, "money"),
    },
    {
      label: "Sell/Current Price",
      sortKey: "sellCurrentPrice",
      render: (row: any) => formatString(row.sellCurrentPrice, "money"),

    },
    {
      label: "Sell Date",
      sortKey: "sellDate",
      render: (row: any) => formatString(row.sellDate, "date"),

    },
    {
      label: "Change",
      sortKey: "change",
      render: (row: any) => formatString(row.change, "percent"),

    },
    {
      label: "S&P 500 Change",
      sortKey: "snp500Change",
      render: (row: any) => formatString(row.snp500Change, "percent"),

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
      <h3>Assets</h3>
      <SortableTable data={data} columns={columns} initialSortKey="ticker" isLoading={assetLoading || assetInfoLoading} errors={[assetError, assetInfoError]}/>
    </>
  );
}
