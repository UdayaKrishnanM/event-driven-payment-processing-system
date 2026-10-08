-- Run after a k6 run, once Kafka consumer lag is back to 0:
--   docker exec -i edps-postgres psql -U postgres -d payments < load-test/check-after-run.sql
-- A passing run has 0 stuck RECEIVED/AUTHORIZED payments and a balanced ledger.

SELECT status, count(*) AS payments FROM payment.payments GROUP BY status ORDER BY status;

SELECT count(*) AS stuck_payments
  FROM payment.payments
 WHERE status IN ('RECEIVED', 'AUTHORIZED') AND created_at < now() - interval '30 seconds';

SELECT count(*) AS unsent_outbox_rows FROM payment.outbox WHERE sent_at IS NULL;

SELECT sum(CASE WHEN direction = 'DEBIT'  THEN amount ELSE 0 END) AS total_debits,
       sum(CASE WHEN direction = 'CREDIT' THEN amount ELSE 0 END) AS total_credits
  FROM ledger.ledger_entries;
