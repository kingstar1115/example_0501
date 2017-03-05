# --- !Ups

ALTER TABLE services
  ADD COLUMN is_car_dependent_price BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE services
SET is_car_dependent_price = TRUE
WHERE key = 'EXTERIOR_CLEANING';

UPDATE services
SET is_car_dependent_price = TRUE
WHERE key = 'EXTERIOR_AND_INTERIOR_CLEANING'
