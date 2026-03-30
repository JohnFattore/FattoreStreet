import { Card } from "react-bootstrap";
import {
  useGetAssetsQuery,
  useGetAssetInfosQuery,
  useGetQuoteQuery,
} from "../functions/api";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { formatString } from "../functions/helperFunctions";
import StateHandler from "./StateHandler";

export default function PortfolioOverview() {
  const { access } = useSelector((state: RootState) => state.user);
  const {
    data: assetsRaw,
    isLoading: assetInfoLoading,
    error: assetError,
  } = useGetAssetsQuery();
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
  const isLoading = assetLoading || assetInfoLoading;

  const { data: quote } = useGetQuoteQuery("SPY");

  if (!assets) {
    return null;
  }

  let portfolioBuyTotal = 0;
  let portfolioCurrentTotal = 0;
  let marketBuyTotal = 0;
  let marketCurrentTotal = 0;

  for (const asset of assets) {
    portfolioBuyTotal += asset.buyPrice;
    marketBuyTotal += asset.snp500PriceBuy;
    if (asset.sellPrice && asset.snp500PriceSell) {
      portfolioCurrentTotal += asset.sellPrice;
      marketCurrentTotal += asset.snp500PriceSell;
    } else {
      portfolioCurrentTotal +=
        (assetInfos[asset.ticker]?.currentPrice ?? 0) * asset.shares;
      marketCurrentTotal += quote?.price ?? 0;
    }
  }

  return (
    <StateHandler
      isLoading={isLoading}
      errors={[assetError, assetInfoError]}
      content={
        <Card>
          <Card.Title>Overview</Card.Title>
          <Card.Body>
            <h6>{`Portfolio Total: ${formatString(
              portfolioCurrentTotal,
              "money"
            )}`}</h6>
            <h6>{`Portfolio Change: ${formatString(
              (portfolioCurrentTotal - portfolioBuyTotal) / portfolioBuyTotal,
              "percent"
            )}`}</h6>
            <h6>{`Market Change: ${formatString(
              (marketCurrentTotal - marketBuyTotal) / marketBuyTotal,
              "percent"
            )}`}</h6>
          </Card.Body>
        </Card>
      }
    />
  );
}
