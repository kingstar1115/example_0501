# --- !Ups

ALTER TABLE jobs
  RENAME TO tasks;

ALTER TABLE task_services
  RENAME job_id TO task_id;

CREATE TABLE IF NOT EXISTS payment_details (
  id             SERIAL PRIMARY KEY,
  task_id        INT         NOT NULL REFERENCES tasks (id),
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
    FROM tasks
)
INSERT INTO payment_details (task_id, payment_method, price, tip, promotion, charge_id)
  SELECT *
  FROM p_details;

ALTER TABLE tasks
  DROP COLUMN payment_method,
  DROP COLUMN price,
  DROP COLUMN tip,
  DROP COLUMN promotion,
  DROP COLUMN charge_id;

# --- !Downs