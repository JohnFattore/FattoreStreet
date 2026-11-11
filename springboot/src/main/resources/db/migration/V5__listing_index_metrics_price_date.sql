--
-- Trade date of the close behind each row's market cap. The index rebuild skips rows whose
-- price is more than a week older than the newest one, so delisted, acquired and renamed
-- tickers (which stop printing on IEX) and tickers that have left the refresh scope (which
-- stop being refreshed) age out of the Fattore indexes instead of lingering on stale metrics.
--

ALTER TABLE public.listing_index_metrics
    ADD COLUMN price_date date;

ALTER TABLE public.listing_index_metrics_aud
    ADD COLUMN price_date date;

-- Backfill from each listing's latest IEX trade date, so no row is left undated: the rebuild
-- treats an undated row as stale, and a partial refresh right after deploy would otherwise date
-- a handful of rows and age every other one out. daily_prices is keyed by IEX symbol (BRK.B),
-- listings by SEC ticker (BRK-B). Rows for dead tickers get their last trade date, so they are
-- excluded from the first rebuild on. The audit table is not backfilled: it records history.
--
-- The newest price comes from ORDER BY ... DESC LIMIT 1 in a LATERAL, which walks the
-- (ticker, trade_date) unique index backwards and stops at one row. Do not write it as
-- max(trade_date) over a join: Postgres only turns min/max into an index lookup for a
-- single-table query, so that form reads every price row of the ticker, once per metrics
-- row, and took the server down for its whole run on the first deploy attempt. Rows whose
-- listing has no prices are not matched and stay NULL.
UPDATE public.listing_index_metrics m
SET price_date = newest.trade_date
FROM public.listings l
CROSS JOIN LATERAL (
    SELECT dp.trade_date
    FROM public.daily_prices dp
    WHERE dp.ticker = replace(l.ticker, '-', '.')
    ORDER BY dp.trade_date DESC
    LIMIT 1
) newest
WHERE l.id = m.listing_id;
