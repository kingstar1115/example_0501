# --- !Ups

DELETE FROM services_extras;
DELETE FROM extras;

INSERT INTO extras (name, description, price)
VALUES ('Air Scent', 'A masculine fragrance infused with sandalwood, bergamot and lemon for an air of mystery.', 200);
INSERT INTO services_extras VALUES (2, 4);
INSERT INTO services_extras VALUES (3, 4);

INSERT INTO extras (name, description, price)
VALUES ('Spray-On Wax', 'Our spray-on Canuaba wax protects and shines the pained surface form daily damage.', 4000);
INSERT INTO services_extras VALUES (1, 5);
INSERT INTO services_extras VALUES (2, 5);

INSERT INTO extras (name, description, price)
VALUES ('Clay Bar & Wax',
        'Clay bar decontaminates and smoothes the paint while fresh layer of Carnauba wax seals, shines & protects it.',
        9500);
INSERT INTO services_extras VALUES (1, 6);
INSERT INTO services_extras VALUES (2, 6);

INSERT INTO extras (name, description, price)
VALUES
  ('Leather Conditioning', 'Safely cleans the leather and restores natural oils to prevent fading and cracking.', 2500);
INSERT INTO services_extras VALUES (2, 7);
INSERT INTO services_extras VALUES (3, 7);

INSERT INTO extras (name, description, price)
VALUES ('Wiper Blades', 'These essentials are recommend to replace before every rainy season.', 4500);
INSERT INTO services_extras VALUES (1, 8);
INSERT INTO services_extras VALUES (2, 8);

INSERT INTO extras (name, description, price)
VALUES ('Washer Fluid top off', 'Don''t get caught without washer fluid when unexpectedly need it', 500);
INSERT INTO services_extras VALUES (1, 9);
INSERT INTO services_extras VALUES (2, 9);