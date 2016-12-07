# --- !Ups

UPDATE settings SET value = '3500' WHERE key = 'carWashing';
UPDATE settings SET value = '1000' WHERE key = 'interiorCleaning';

# --- !Downs

UPDATE settings SET value = '4000' WHERE key = 'carWashing';
UPDATE settings SET value = '500' WHERE key = 'interiorCleaning';