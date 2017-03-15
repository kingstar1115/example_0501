# --- !Ups

ALTER TABLE payment_details
  ADD COLUMN created_date TIMESTAMP NOT NULL DEFAULT now();

ALTER TABLE settings
  ADD COLUMN created_date TIMESTAMP NOT NULL DEFAULT now();