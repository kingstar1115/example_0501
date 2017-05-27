# --- !Ups

UPDATE services SET name = 'Exterior Only' WHERE key = 'EXTERIOR_CLEANING';
UPDATE services SET name = 'Exterior & Interior' WHERE key = 'EXTERIOR_AND_INTERIOR_CLEANING';