import { ISECData } from "../interfaces";

// Every figure the comparison table shows, by the name Django expects. Django
// accepts only these, so nothing else on the fact sheet reaches the model.
const analysisFields: Record<string, keyof ISECData> = {
  market_cap: "marketCap",
  market_cap_as_of: "marketCapAsOf",
  ttm_revenue: "ttmRevenue",
  ttm_revenue_yoy: "ttmRevenueYoY",
  ttm_gross_profit: "ttmGrossProfit",
  ttm_operating_income: "ttmOperatingIncome",
  ttm_net_income: "ttmNetIncome",
  ttm_net_income_yoy: "ttmNetIncomeYoY",
  ttm_operating_cash_flow: "ttmOperatingCashFlow",
  latest_assets: "latestAssets",
  latest_liabilities: "latestLiabilities",
  latest_equity: "latestEquity",
  latest_cash: "latestCash",
  latest_inventory: "latestInventory",
  latest_eps: "latestEps",
  latest_quarter_end: "latestQuarterEnd",
  net_margin: "netMargin",
  gross_margin: "grossMargin",
  ro_a: "roA",
  debt_to_assets: "debtToAssets",
  cash_to_liabilities: "cashToLiabilities",
  ocf_to_net_income: "ocfToNetIncome",
};

export function toAnalysisFigures(data: ISECData) {
  const figures: Record<string, string | null> = { ticker: data.ticker };
  for (const [field, key] of Object.entries(analysisFields)) {
    figures[field] = data[key] ?? null;
  }
  return figures;
}
