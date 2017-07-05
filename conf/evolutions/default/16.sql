# --- !Ups
ALTER TABLE services
  ADD COLUMN sequence INT;

UPDATE services
SET sequence  = 1,
  description = 'Qweex RINSELESS Wash® <br/>' ||
                'Spray-On Carnauba Wax <br/>' ||
                'Wheels and Tires <br/>' ||
                'No-Grease Tire Shine <br/>' ||
                'Windows'
WHERE key = 'EXTERIOR_CLEANING';

UPDATE services
SET sequence  = 2,
  description = 'Qweex Waterless Wash® <br/>' ||
                'Spray-On Carnauba Wax <br/>' ||
                'Wheels and Tires <br/>' ||
                'No-Grease Tire Shine <br/>' ||
                'Windows <br/>' ||
                'Interior Vacuum <br/>' ||
                'Leather Conditioning'
WHERE key = 'EXTERIOR_AND_INTERIOR_CLEANING';

UPDATE services
SET sequence = 3
WHERE key = 'INTERIOR_CLEANING';

ALTER TABLE services
  ALTER COLUMN sequence SET NOT NULL;

# --- !Downs
ALTER TABLE services
  DROP COLUMN IF EXISTS sequence;
