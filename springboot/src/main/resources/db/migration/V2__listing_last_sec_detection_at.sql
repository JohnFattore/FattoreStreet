--
-- Tracks when SEC corporate-action detection last ran per listing so the nightly
-- adjustment can re-detect on a rolling ~weekly cadence instead of never (or all at once).
-- Not audited (no listings_aud column): nightly stamps would only generate revision noise.
--

ALTER TABLE public.listings
    ADD COLUMN last_sec_detection_at timestamp(6) without time zone;
