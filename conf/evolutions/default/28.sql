# --- !Ups

DELETE FROM time_slots
WHERE start_time = (TIME '07:00:00')
      AND reserved = 0
      AND day_slot_id IN (SELECT id
                          FROM day_slots
                          WHERE date >= current_date);

UPDATE settings
SET value = 9
WHERE key = 'day.slot.capacity';