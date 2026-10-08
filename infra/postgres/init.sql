-- Runs once, the first time the Postgres container starts with an empty data volume.
-- Database-per-service: one schema per service, each owned by its own login role.
-- A service can only read and write its own schema, so "no service reads another service's tables" is enforced
-- by the database, not just by convention.

CREATE ROLE payment_svc      LOGIN PASSWORD 'payment_pwd';
CREATE ROLE ledger_svc       LOGIN PASSWORD 'ledger_pwd';
CREATE ROLE notification_svc LOGIN PASSWORD 'notification_pwd';

CREATE SCHEMA payment      AUTHORIZATION payment_svc;
CREATE SCHEMA ledger       AUTHORIZATION ledger_svc;
CREATE SCHEMA notification AUTHORIZATION notification_svc;

-- Nobody but the owner may use a schema.
REVOKE ALL ON SCHEMA payment, ledger, notification FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- Each role looks in its own schema first.
ALTER ROLE payment_svc      SET search_path = payment;
ALTER ROLE ledger_svc       SET search_path = ledger;
ALTER ROLE notification_svc SET search_path = notification;
