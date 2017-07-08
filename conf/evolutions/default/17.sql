# --- !Ups

UPDATE settings
SET value = 12
WHERE key = 'day.slot.capacity';

# --- !Downs
UPDATE settings
SET value = 8
WHERE key = 'day.slot.capacity';
