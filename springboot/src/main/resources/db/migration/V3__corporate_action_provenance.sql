--
-- Adds effective/ex-date provenance tracking to corporate actions and the
-- SEC_PRICE_CORROBORATED source type for splits detected from IEX price breaks
-- confirmed against SEC filing dates.
--

ALTER TABLE public.corporate_actions
    DROP CONSTRAINT corporate_actions_source_type_check;

ALTER TABLE public.corporate_actions
    ADD CONSTRAINT corporate_actions_source_type_check
    CHECK (((source_type)::text = ANY ((ARRAY['SEC_EQUITY_XBRL'::character varying, 'SEC_ETF_FILING'::character varying, 'SEC_PRICE_CORROBORATED'::character varying, 'MANUAL'::character varying, 'OTHER'::character varying])::text[])));

ALTER TABLE public.corporate_actions_aud
    DROP CONSTRAINT corporate_actions_aud_source_type_check;

ALTER TABLE public.corporate_actions_aud
    ADD CONSTRAINT corporate_actions_aud_source_type_check
    CHECK (((source_type)::text = ANY ((ARRAY['SEC_EQUITY_XBRL'::character varying, 'SEC_ETF_FILING'::character varying, 'SEC_PRICE_CORROBORATED'::character varying, 'MANUAL'::character varying, 'OTHER'::character varying])::text[])));

ALTER TABLE public.corporate_actions
    ADD COLUMN ex_date_source character varying(24);

ALTER TABLE public.corporate_actions_aud
    ADD COLUMN ex_date_source character varying(24);
