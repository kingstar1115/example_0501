# --- !Ups

UPDATE services
SET enabled = TRUE, name = 'Interior Only'
WHERE key = 'INTERIOR_CLEANING';

INSERT INTO services (name, price, key, deletable, sequence)
VALUES ('Wiper Blades', 5000, 'WIPER_BLADES', TRUE, 4);

# --- !Downs

UPDATE services
SET enabled = FALSE, name = 'Interior cleaning'
WHERE key = 'INTERIOR_CLEANING';

DELETE FROM services
WHERE key = 'WIPER_BLADES';
