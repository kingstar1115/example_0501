# --- !Ups

CREATE TABLE day_slots (
  id           SERIAL PRIMARY KEY,
  created_date TIMESTAMP NOT NULL DEFAULT now(),
  date         DATE      NOT NULL
);

CREATE TABLE time_slots (
  id             SERIAL PRIMARY KEY,
  created_date   TIMESTAMP NOT NULL DEFAULT now(),
  capacity       INT       NOT NULL,
  bookings_count INT       NOT NULL DEFAULT 0,
  start_time     TIME      NOT NULL,
  end_time       TIME      NOT NULL,
  day_slot_id    INT       NOT NULL REFERENCES day_slots (id)
);

ALTER TABLE tasks
  ADD COLUMN time_slot_id INT REFERENCES time_slots (id);

INSERT INTO settings (key, value) VALUES ('day.slot.capacity', 8);
INSERT INTO settings (key, value) VALUES ('time.slot.capacity', 2);