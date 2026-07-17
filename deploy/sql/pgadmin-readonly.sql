-- Read-only Postgres role for pgadmin4 (least privilege).
--
-- ONE-TIME, on the EC2 host, against the postgres container as the superuser
-- (local socket auth inside the container is trusted). Fetch the role password
-- from fattorestreet/env first (key PGADMIN_RO_DB_PASSWORD, add it to the
-- secret if missing):
--
--   PW=$(aws secretsmanager get-secret-value --secret-id "$SECRETS_ARN" \
--         --query SecretString --output text | jq -r .PGADMIN_RO_DB_PASSWORD)
--   sudo docker exec -i postgres psql -U postgres -v ON_ERROR_STOP=1 \
--     -v ro_password="'$PW'" -f - < deploy/sql/pgadmin-readonly.sql
--
-- Then in the pgadmin UI, register (or edit) the server connection to use
-- username pgadmin_ro instead of postgres. Re-running fails on CREATE ROLE
-- once the role exists; to rotate the password use
-- ALTER ROLE pgadmin_ro PASSWORD '<new>' instead.

CREATE ROLE pgadmin_ro LOGIN PASSWORD :ro_password
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;

-- Defense in depth: sessions default to read-only transactions. The grants
-- below are the real boundary; this just makes accidental writes fail fast.
ALTER ROLE pgadmin_ro SET default_transaction_read_only = on;

GRANT CONNECT ON DATABASE postgres TO pgadmin_ro;
GRANT CONNECT ON DATABASE springboot TO pgadmin_ro;

-- postgres db (Django)
\connect postgres
GRANT USAGE ON SCHEMA public TO pgadmin_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO pgadmin_ro;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO pgadmin_ro;
-- Cover tables created by future migrations (which run as postgres)
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  GRANT SELECT ON TABLES TO pgadmin_ro;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  GRANT SELECT ON SEQUENCES TO pgadmin_ro;

-- springboot db (Spring Boot)
\connect springboot
GRANT USAGE ON SCHEMA public TO pgadmin_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO pgadmin_ro;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO pgadmin_ro;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  GRANT SELECT ON TABLES TO pgadmin_ro;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  GRANT SELECT ON SEQUENCES TO pgadmin_ro;
