# --- !Ups
ALTER TABLE tasks
  ALTER COLUMN time_slot_id SET NOT NULL;

# --- !Downs
ALTER TABLE tasks
  ALTER COLUMN time_slot_id DROP NOT NULL;