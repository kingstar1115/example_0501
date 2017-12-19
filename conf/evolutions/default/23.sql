# --- !Ups

ALTER TABLE tasks
  ADD COLUMN job_hash VARCHAR(64)

# --- !Downs

ALTER TABLE tasks
  DROP COLUMN IF EXISTS job_hash;