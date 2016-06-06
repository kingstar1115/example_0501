# --- !Ups

INSERT INTO settings (key, value) VALUES ('carWashing', '4000');
INSERT INTO settings (key, value) VALUES ('interiorCleaning', '500');

  # --- !Downs
DELETE FROM settings;