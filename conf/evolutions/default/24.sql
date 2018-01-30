# --- !Ups

ALTER TABLE agents
  ADD COLUMN avr_customer_rating DECIMAL NOT NULL DEFAULT 0;

ALTER TABLE tasks
  ADD COLUMN rating INTEGER;

UPDATE extras SET description = 'Masculine fragrance infused with sandalwood, bergamot and lemon.'
  WHERE name = 'Air Scent';
UPDATE extras SET description = 'Fresh layer of premium, non-abrasive Carnauba wax for unsurpassed shine, depth and protection.'
  WHERE name = 'Spray-On Wax';
UPDATE extras SET description = 'Surface is gently decontaminated to a "smooth-as-glass" feel, then sealed by a layer of Carnauba wax.'
  WHERE name = 'Clay Bar & Wax';
UPDATE extras SET description = 'Cleaned and conditioned leather efficiently helps to preserve its strength, durability, and appearance.', name = 'Deep Leather Conditioning'
  WHERE name = 'Leather Conditioning';
UPDATE extras SET description = 'New set of premium blades delivered and installed for a smooth, clean and streak-free wipe.', name = 'Wiper Blades Replacement'
  WHERE name = 'Wiper Blades';
UPDATE extras SET description = 'Enhance visibility with Rain-X water repellent windshield washer fluid. Delivered and refilled.'
  WHERE name = 'Washer Fluid Top-Off';

# --- !Downs

ALTER TABLE agents
  DROP COLUMN IF EXISTS avr_customer_rating;

ALTER TABLE tasks
  DROP COLUMN IF EXISTS rating;