# --- !Ups

ALTER TABLE vehicles
  ALTER COLUMN maker_id TYPE VARCHAR(100) USING (maker_id :: varchar(100));