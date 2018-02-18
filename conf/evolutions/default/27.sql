# --- !Ups

ALTER TABLE vehicles
  ALTER COLUMN source SET NOT NULL,
  ALTER COLUMN vehicle_size_class SET NOT NULL;

# --- !Downs

ALTER TABLE vehicles
  ALTER COLUMN source DROP NOT NULL,
  ALTER COLUMN vehicle_size_class DROP NOT NULL;