# --- !Ups

INSERT INTO settings (key, value) VALUES ('compactWashing', '2000');
INSERT INTO settings (key, value) VALUES ('sedanWashing', '2500');
INSERT INTO settings (key, value) VALUES ('suvWashing', '3000');
INSERT INTO settings (key, value) VALUES ('interiorCleaning', '500');

  # --- !Downs
DELETE FROM settings;