# --- !Ups

CREATE TABLE IF NOT EXISTS payment_details (
  id             SERIAL PRIMARY KEY,
  job_id         INT         NOT NULL REFERENCES jobs (id),
  payment_method VARCHAR(32) NOT NULL,
  price          INT         NOT NULL,
  tip            INT         NOT NULL DEFAULT 0,
  promotion      INT         NOT NULL DEFAULT 0,
  charge_id      VARCHAR(128)
);

WITH p_details AS (
    SELECT
      id,
      payment_method,
      price,
      tip,
      promotion,
      charge_id
    FROM jobs
)
INSERT INTO payment_details (job_id, payment_method, price, tip, promotion, charge_id)
  SELECT *
  FROM p_details;

ALTER TABLE jobs
  DROP COLUMN payment_method,
  DROP COLUMN price,
  DROP COLUMN tip,
  DROP COLUMN promotion,
  DROP COLUMN charge_id;

# --- !Downs