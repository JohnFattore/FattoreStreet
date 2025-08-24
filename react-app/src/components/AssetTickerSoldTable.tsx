import { Button} from "react-bootstrap";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import {
  useGetAssetsQuery,
  useGetAssetInfosQuery,
} from "../functions/api";
import { formatString } from "../functions/helperFunctions";
import { useState } from "react";
import { IAsset } from "../interfaces";
import { SortableTable } from "./SortableTable";
import AssetDeleteSellModal from "./AssetDeleteSellModal";

export default function AssetTickerSoldTable({ ticker }) {
  const {
    data: rawAllAssets,
    isLoading: assetLoading,
    error: assetError,
  } = useGetAssetsQuery();
  const allAssets = rawAllAssets ?? [];
  const assets = allAssets?.filter((a) => a.ticker === ticker);
  const {
    data: rawAssetInfos,
    isLoading: assetInfoLoading,
    error: assetInfoError,
  } = useGetAssetInfosQuery([ticker]);
  const assetInfos = rawAssetInfos ?? {};
  const { access } = useSelector((state: RootState) => state.user);
  const isLoading = assetLoading || assetInfoLoading;

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

  const assetsSold = assets.filter((item) => item.sellDate);

  if (assetsSold.length == 0 && access && !isLoading) {
    return null;
  }
  
  const info = assetInfos[ticker];
  const data = assetsSold.map((asset) => {
    if (!asset.sellPrice) {
      throw Error(`ticker ${asset.ticker} as no sell price`)
    }
    return {
      id: asset.id,
      ticker: asset.ticker,
      shares: asset.shares,
      buyDate: asset.buyDate,
      buyPrice: asset.buyPrice,
      sellDate: asset.sellDate,
      sellPrice: asset.sellPrice,
      percentChange: (asset.sellPrice - asset.buyPrice) / asset.buyPrice,
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
      label: "Sell Date",
      sortKey: "sellDate",
      render: (row: any) => formatString(row.sellDate, "date"),
    },
    {
      label: "Sell Price",
      sortKey: "sellPrice",
      render: (row: any) => formatString(row.sellPrice, "money"),
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
        setSelectedAsset(assets.find((asset) => asset.id === row.id));
        return (
          <Button
            onClick={handleShowSell}
          >{`Sell ${selectedAsset?.ticker}`}</Button>
        );
      },
    },
    {
      label: "Delete Asset",
      sortKey: "deleteAsset",
      sortable: false,
      render: (row: any) => {
        setSelectedAsset(assets.find((asset) => asset.id === row.id));
        return (
          <Button
            onClick={handleShowDelete}
          >{`Delete ${selectedAsset?.ticker}`}</Button>
        );
      },
    },
  ];

  return (
    <>
      <h3>Sold Assets</h3>
      <SortableTable
        data={data}
        columns={columns}
        initialSortKey="ticker"
        isLoading={isLoading}
        errors={[assetError, assetInfoError]}
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
