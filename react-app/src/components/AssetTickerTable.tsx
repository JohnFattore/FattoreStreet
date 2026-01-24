import { useSelector } from "react-redux";
import { RootState } from "../main";
import { useGetAssetsQuery, useGetAssetInfosQuery, useGetAccountsQuery } from "../functions/api";
import { formatString } from "../functions/helperFunctions";
import { useState } from "react";
import { SortableTable } from "./SortableTable";
import { Button } from "react-bootstrap";
import { IAsset } from "../interfaces";
import AssetDeleteSellModal from "./AssetDeleteSellModal";

export default function AssetTickerTable({ ticker }) {
  const { access } = useSelector((state: RootState) => state.user);
  const {
    data: rawAllAssets,
    isLoading: assetLoading,
    error: assetError,
  } = useGetAssetsQuery();
  const allAssets = rawAllAssets ?? [];
  const assets = allAssets?.filter((a) => a.ticker === ticker);

  const { data: accountsRaw, isLoading: accountsLoading, error: accountsError } = useGetAccountsQuery(undefined, {
    skip: !access,
  });
  const accounts = accountsRaw ?? [];

  const {
    data: rawAssetInfos,
    isLoading: assetInfoLoading,
    error: assetInfoError,
  } = useGetAssetInfosQuery([ticker]);
  const assetInfos = rawAssetInfos ?? {};

  const isLoading = assetLoading || assetInfoLoading || accountsLoading;

  const [selectedAsset, setSelectedAsset] = useState<IAsset>();

  const [showSell, setShowSell] = useState(false);
  const handleCloseSell = () => setShowSell(false);
  const handleShowSell = () => setShowSell(true);

  const [showDelete, setShowDelete] = useState(false);
  const handleCloseDelete = () => setShowDelete(false);
  const handleShowDelete = () => setShowDelete(true);

  if (!access) {
    return null;
  }
  const assetsOwned = assets.filter((item) => !item.sellDate);

  if (assetsOwned.length == 0 && access && !isLoading) {
    return null;
  }

  const data: any[] = [];
  const info = assetInfos[ticker];
  for (const asset of assetsOwned) {
    const account = accounts.find((a) => a.id === asset.account);
    data.push({
      id: asset.id,
      ticker: asset.ticker,
      shares: asset.shares,
      accountName: account?.name ?? "N/A",
      buyDate: asset.buyDate,
      buyPrice: asset.buyPrice,
      currentPrice: asset.shares * info?.currentPrice,
      percentChange:
        (asset.shares * info?.currentPrice - asset.buyPrice) / asset.buyPrice,
    });
  }

  const columns = [
    {
      label: "Ticker",
      sortKey: "ticker",
    },
    {
      label: "Account",
      sortKey: "accountName",
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
      label: "Current Price",
      sortKey: "currentPrice",
      render: (row: any) =>
        row.currentPrice !== null
          ? formatString(row.currentPrice, "money")
          : "N/A",
    },
    {
      label: "Percent Change",
      sortKey: "percentChange",
      render: (row: any) =>
        row.percentChange !== null
          ? formatString(row.percentChange, "percent")
          : "N/A",
    },
    {
      label: "Sell Asset",
      sortKey: "sellAsset",
      sortable: false,
      render: (row: any) => {
        return (
          <Button
            onClick={() => {
              setSelectedAsset(assets.find((asset) => asset.id === row.id));
              handleShowSell();
            }}
          >{`Sell ${ticker}`}</Button>
        );
      },
    },
    {
      label: "Delete Asset",
      sortKey: "deleteAsset",
      sortable: false,
      render: (row: any) => {
        return (
          <Button
            onClick={() => {
              setSelectedAsset(assets.find((asset) => asset.id === row.id));
              handleShowDelete();
            }}
          >{`Delete ${ticker}`}</Button>
        );
      },
    },
  ];

  return (
    <>
      <h3>Purchased Assets</h3>
      <SortableTable
        data={data}
        columns={columns}
        initialSortKey="ticker"
        isLoading={isLoading}
        errors={[assetError, assetInfoError, accountsError]}
      />
      <AssetDeleteSellModal
        asset={selectedAsset}
        showSell={showSell}
        showDelete={showDelete}
        handleCloseSell={handleCloseSell}
        handleCloseDelete={handleCloseDelete}
      />
    </>
  );
}