--
-- Company search by name (GET /listings/search). Trigram indexes let a partial or misspelled
-- name ("micrsoft") find its listing without scanning every row. pg_trgm ships with Postgres and
-- is a trusted extension, so the migration role can create it without superuser.
--
-- The indexes are on lower(...) because the search lowercases the query and compares with the
-- word-similarity operator (<%), which a gin_trgm_ops index serves. sec_series_name is empty for
-- most funds today (the SEC fund ticker file carries no names); it is indexed now so fund names
-- become searchable as soon as they are loaded.
--

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX listings_title_trgm_idx
    ON public.listings USING gin (lower(title) gin_trgm_ops);

CREATE INDEX listings_sec_series_name_trgm_idx
    ON public.listings USING gin (lower(sec_series_name) gin_trgm_ops);
