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
