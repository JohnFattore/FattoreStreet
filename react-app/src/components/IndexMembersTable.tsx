import { SortableTable } from "./SortableTable";
import { IIndexMemberRow } from "../interfaces";
import {
  formatNumber,
  formatPercent,
  formatLargeCurrency,
  formatLargeNumber,
} from "../functions/helperFunctions";

type Props = {
  members: IIndexMemberRow[];
  isLoading: boolean;
  errors: unknown[];
};

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
    stateHQ: m.stock.stateHQ,
    stateIncorp: m.stock.stateIncorp,
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
          render: (r) => formatLargeCurrency(r.freeFloatMarketCap),
        },
        {
          label: "Mkt Cap",
          sortKey: "marketCap",
          render: (r) => formatLargeCurrency(r.marketCap),
        },
        {
          label: "Vol USD",
          sortKey: "volumeUSD",
          render: (r) => formatLargeCurrency(r.volumeUSD),
        },
        {
          label: "Vol",
          sortKey: "volume",
          render: (r) => formatLargeNumber(r.volume),
        },
        {
          label: "Float",
          sortKey: "freeFloat",
          render: (r) => formatNumber(r.freeFloat),
        },
        { label: "Type", sortKey: "securityType" },
        { label: "HQ", sortKey: "countryHQ" },
        {
          label: "HQ st",
          sortKey: "stateHQ",
          render: (r) => r.stateHQ ?? "—",
        },
        { label: "Incorp", sortKey: "countryIncorp" },
        {
          label: "Incorp st",
          sortKey: "stateIncorp",
          render: (r) => r.stateIncorp ?? "—",
        },
      ]}
    />
  );
}
