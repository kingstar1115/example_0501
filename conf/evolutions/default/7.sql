# --- !Ups

ALTER TABLE services
  ALTER COLUMN description SET DATA TYPE TEXT;
ALTER TABLE extras
  ALTER COLUMN description SET DATA TYPE TEXT;

# --- !Downs
ALTER TABLE services
  ALTER COLUMN description SET DATA TYPE VARCHAR;
ALTER TABLE extras
  ALTER COLUMN description SET DATA TYPE VARCHAR;