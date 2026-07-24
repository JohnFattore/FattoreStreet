import {
  PieChart,
  Pie,
  Cell,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts";
import { useGetAssetsQuery, useGetAssetInfosQuery } from "../functions/api";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { formatString } from "../functions/helperFunctions";
import { Card } from "react-bootstrap";
import StateHandler from "./StateHandler";

const COLORS = [
  "#0088FE",
  "#00C49F",
  "#FFBB28",
  "#FF8042",
  "#AF19FF",
  "#FF4560",
  "#00C0EF",
];

export default function AssetPieChart() {
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

  if (!access) return null;

  const assetsOwned = assets.filter((item) => !item.sellDate);

  if (assetsOwned.length === 0 && !isLoading) return null;

  let totalValue = 0;

  const assetsByTicker: Record<string, number> = {};
  for (const asset of assetsOwned) {
    assetsByTicker[asset.ticker] =
      (assetsByTicker[asset.ticker] || 0) +
      assetInfos[asset.ticker]?.currentPrice * asset.shares;
    totalValue += assetInfos[asset.ticker]?.currentPrice * asset.shares;
  }

  const data: { name: string; value: number }[] = [];

  Object.entries(assetsByTicker).forEach(([ticker, currentPrice]) => {
    data.push({
      name: ticker,
      value: Number(((100 * currentPrice) / totalValue).toFixed(2)),
    });
  });

  return (
    <StateHandler
      isLoading={isLoading}
      errors={[assetError, assetInfoError]}
      content={
        <Card>
          <Card.Title>Portfolio Pie Chart</Card.Title>
          <div style={{ width: "100%", height: 700 }}>
            <ResponsiveContainer>
              <PieChart>
                <Pie
                  data={data}
                  dataKey="value"
                  nameKey="name"
                  fill="#8884d8"
                  label={(entry) =>
                    `${entry.name}: ${formatString(
                      (entry.value ?? 0) / 100,
                      "percent",
                    )}`
                  }
                >
                  {data.map((_, index) => (
                    <Cell
                      key={`cell-${index}`}
                      fill={COLORS[index % COLORS.length]}
                    />
                  ))}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </Card>
      }
    />
  );
}
