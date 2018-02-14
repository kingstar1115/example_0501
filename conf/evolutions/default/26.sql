# --- !Ups

ALTER TABLE vehicles
  ADD COLUMN source VARCHAR(30),
  ADD COLUMN vehicle_size_class VARCHAR(150);

# --- !Downs

ALTER TABLE vehicles
  DROP COLUMN IF EXISTS source,
  DROP COLUMN IF EXISTS vehicle_size_class;