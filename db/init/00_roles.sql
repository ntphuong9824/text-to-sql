-- Roles, schemas and default settings for local development only.
CREATE ROLE app_rw LOGIN PASSWORD 'app_rw_dev';
CREATE ROLE analyst_ro LOGIN PASSWORD 'analyst_ro_dev';

CREATE SCHEMA chinook AUTHORIZATION postgres;
CREATE SCHEMA app AUTHORIZATION app_rw;

REVOKE ALL ON SCHEMA public FROM PUBLIC;

ALTER ROLE analyst_ro SET default_transaction_read_only = on;
ALTER ROLE analyst_ro SET statement_timeout = '10s';
ALTER ROLE analyst_ro SET search_path = dw;
ALTER ROLE app_rw SET search_path = app;
