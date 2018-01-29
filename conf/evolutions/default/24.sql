# --- !Ups

ALTER TABLE agents
  ADD COLUMN avr_customer_rating DECIMAL NOT NULL DEFAULT 0;

ALTER TABLE tasks
  ADD COLUMN rating INTEGER;

# --- !Downs

ALTER TABLE agents
  DROP COLUMN IF EXISTS avr_customer_rating;

ALTER TABLE tasks
  DROP COLUMN IF EXISTS rating;