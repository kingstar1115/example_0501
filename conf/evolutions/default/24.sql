# --- !Ups

ALTER TABLE agents
  ADD COLUMN avr_customer_rating DECIMAL NOT NULL DEFAULT 0;

# --- !Downs

ALTER TABLE agents
  DROP COLUMN IF EXISTS avr_customer_rating;