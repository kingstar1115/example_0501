# --- !Ups

INSERT INTO settings (key, value) VALUES ('service.additional.cost', '1000');

# --- !Downs
DELETE FROM settings
WHERE key = 'service.additional.cost';