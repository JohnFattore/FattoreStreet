import { SortableTable } from "./SortableTable";
import { IIndexMemberRow } from "../interfaces";

type Props = {
  members: IIndexMemberRow[];
  isLoading: boolean;
  errors: any[];
};

function formatNumber(v: number | null | undefined) {
  if (v == null || Number.isNaN(v)) return "";
  return Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(v);
}

function formatCurrency(v: number | null | undefined) {
  if (v == null || Number.isNaN(v)) return "";
  return Intl.NumberFormat(undefined, {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 0,
  }).format(v);
}

function formatPercent(v: number | null | undefined) {
  if (v == null || Number.isNaN(v)) return "";
  return `${v.toFixed(2)}%`;
}

export function IndexMembersTable({ members, isLoading, errors }: Props) {
  const rows = members.map((m) => ({
    id: m.id,
    ticker: m.stock.ticker,
    name: m.stock.name,
    weightPercent: m.percent,
    freeFloatMarketCap: m.stock.freeFloatMarketCap,
    marketCap: m.stock.marketCap,
    volumeUSD: m.stock.volumeUSD,
    volume: m.stock.volume,
    freeFloat: m.stock.freeFloat,
    securityType: m.stock.securityType,
    countryHQ: m.stock.countryHQ,
    countryIncorp: m.stock.countryIncorp,
  }));

  return (
    <SortableTable
      data={rows}
      isLoading={isLoading}
      errors={errors}
      initialSortKey="weightPercent"
      initialSortDirection="desc"
      columns={[
        { label: "Ticker", sortKey: "ticker" },
        { label: "Name", sortKey: "name" },
        {
          label: "Weight",
          sortKey: "weightPercent",
          render: (r) => formatPercent(r.weightPercent),
        },
        {
          label: "FF Mkt Cap",
          sortKey: "freeFloatMarketCap",
          render: (r) => formatCurrency(r.freeFloatMarketCap),
        },
        {
          label: "Mkt Cap",
          sortKey: "marketCap",
          render: (r) => formatCurrency(r.marketCap),
        },
        {
          label: "Vol USD",
          sortKey: "volumeUSD",
          render: (r) => formatCurrency(r.volumeUSD),
        },
        {
          label: "Vol",
          sortKey: "volume",
          render: (r) => formatNumber(r.volume),
        },
        {
          label: "Float",
          sortKey: "freeFloat",
          render: (r) => formatNumber(r.freeFloat),
        },
        { label: "Type", sortKey: "securityType" },
        { label: "HQ", sortKey: "countryHQ" },
        { label: "Incorp", sortKey: "countryIncorp" },
      ]}
    />
  );
}

