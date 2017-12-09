# --- !Ups

DELETE FROM time_slots
WHERE start_time >= (TIME '17:00:00')
      AND reserved = 0
      AND day_slot_id IN (SELECT id
                          FROM day_slots
                          WHERE date >= current_date);

UPDATE settings
SET value = 10
WHERE key = 'day.slot.capacity';