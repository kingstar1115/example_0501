# --- !Ups

ALTER TABLE jobs ADD COLUMN charge_id VARCHAR(255);

# --- !Downs

ALTER TABLE jobs DROP COLUMN charge_id IF EXISTS