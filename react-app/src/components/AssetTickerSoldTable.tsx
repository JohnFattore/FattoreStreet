import { Button } from "react-bootstrap";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import {
  useGetAssetsQuery,
  useGetAssetInfosQuery,
  useGetAccountsQuery,
} from "../functions/api";
import { formatString } from "../functions/helperFunctions";
import { useState } from "react";
import { IAsset } from "../interfaces";
import { SortableTable } from "./SortableTable";
import AssetDeleteSellModal from "./AssetDeleteSellModal";

type AssetTickerSoldRow = {
  id: number;
  ticker: string;
  accountName: string;
  shares: number;
  buyDate: string;
  buyPrice: number;
  sellDate: string | null;
  sellPrice: number;
  percentChange: number;
  shortName: string;
  hasError: boolean;
};

export default function AssetTickerSoldTable({ ticker }: { ticker: string }) {
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

  const assetsSold = assets.filter((item) => item.sellDate);

  if (assetsSold.length == 0 && access && !isLoading) {
    return null;
  }

  const info = assetInfos[ticker];
  const data: AssetTickerSoldRow[] = assetsSold.map((asset) => {
    if (!asset.sellPrice) {
      throw Error(`ticker ${asset.ticker} as no sell price`)
    }
    const account = accounts.find((a) => a.id === asset.account);
    return {
      id: asset.id,
      ticker: asset.ticker,
      accountName: account?.name ?? "N/A",
      shares: asset.shares,
      buyDate: asset.buyDate,
      buyPrice: asset.buyPrice,
      sellDate: asset.sellDate,
      sellPrice: asset.sellPrice,
      percentChange: (asset.sellPrice - asset.buyPrice) / asset.buyPrice,
      shortName: assetInfoLoading ? "Loading..." : (info?.shortName ?? "N/A"),
      hasError: !info && !assetInfoLoading,
    };
  });

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
      render: (row: AssetTickerSoldRow) => formatString(row.shares, "amount"),
    },
    {
      label: "Buy Date",
      sortKey: "buyDate",
      render: (row: AssetTickerSoldRow) => formatString(row.buyDate, "date"),
    },
    {
      label: "Buy Price",
      sortKey: "buyPrice",
      render: (row: AssetTickerSoldRow) => formatString(row.buyPrice, "money"),
    },
    {
      label: "Sell Date",
      sortKey: "sellDate",
      render: (row: AssetTickerSoldRow) =>
        row.sellDate != null ? formatString(row.sellDate, "date") : "N/A",
    },
    {
      label: "Sell Price",
      sortKey: "sellPrice",
      render: (row: AssetTickerSoldRow) => formatString(row.sellPrice, "money"),
    },
    {
      label: "Percent Change",
      sortKey: "percentChange",
      render: (row: AssetTickerSoldRow) =>
        row.percentChange !== null
          ? formatString(row.percentChange, "percent")
          : "N/A",
    },
    {
      label: "Sell Asset",
      sortKey: "sellAsset",
      sortable: false,
      render: (row: AssetTickerSoldRow) => {
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
      render: (row: AssetTickerSoldRow) => {
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
      <h3>Sold Assets</h3>
      <SortableTable
        data={data}
        columns={columns}
        initialSortKey="ticker"
        isLoading={assetLoading || accountsLoading}
        errors={[assetError, accountsError]}
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
