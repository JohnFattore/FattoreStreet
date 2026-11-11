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
UPDATE public.listing_index_metrics m
SET price_date = (
    SELECT max(dp.trade_date)
    FROM public.daily_prices dp
    JOIN public.listings l ON dp.ticker = replace(l.ticker, '-', '.')
    WHERE l.id = m.listing_id
);
