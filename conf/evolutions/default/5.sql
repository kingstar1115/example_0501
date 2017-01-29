# --- !Ups

CREATE TABLE IF NOT EXISTS accommodations (
  id           SERIAL PRIMARY KEY,
  created_date TIMESTAMP NOT NULL DEFAULT now(),
  name         VARCHAR   NOT NULL UNIQUE,
  description  VARCHAR,
  price        INT       NOT NULL,
  key          VARCHAR(64)
);

INSERT INTO accommodations (name, price, key) VALUES ('Exterior cleaning', 3500, 'EXTERIOR_CLEANING');
INSERT INTO accommodations (name, price, key)
VALUES ('Exterior and interior cleaning', 4500, 'EXTERIOR_AND_INTERIOR_CLEANING');
INSERT INTO accommodations (name, price) VALUES ('Interior cleaning', 2500);

CREATE TABLE IF NOT EXISTS extras (
  id           SERIAL PRIMARY KEY,
  created_date TIMESTAMP NOT NULL DEFAULT now(),
  name         VARCHAR   NOT NULL UNIQUE,
  description  VARCHAR,
  price        INT       NOT NULL
);

INSERT INTO extras (name, price) VALUES ('Carnauba Wax', 1000);
INSERT INTO extras (name, price) VALUES ('Clay Bar Treatment', 1000);
INSERT INTO extras (name, price) VALUES ('Leather and Plastic conditioning', 1000);

CREATE TABLE IF NOT EXISTS accommodations_extras (
  service_id INT NOT NULL REFERENCES accommodations (id),
  extra_id   INT NOT NULL REFERENCES extras (id),
  PRIMARY KEY (service_id, extra_id)
);

INSERT INTO accommodations_extras VALUES (1, 1);
INSERT INTO accommodations_extras VALUES (1, 2);

INSERT INTO accommodations_extras VALUES (2, 1);
INSERT INTO accommodations_extras VALUES (2, 2);
INSERT INTO accommodations_extras VALUES (2, 3);

INSERT INTO accommodations_extras VALUES (3, 3);

CREATE TABLE IF NOT EXISTS task_services (
  id           SERIAL PRIMARY KEY,
  created_date TIMESTAMP NOT NULL DEFAULT now(),
  name         VARCHAR   NOT NULL,
  price        INT       NOT NULL,
  job_id       INT       NOT NULL REFERENCES jobs (id)
);

WITH tasks AS (
    SELECT
      'Exterior cleaning' AS name,
      id,
      price
    FROM jobs
    WHERE has_interior_cleaning = FALSE
)
INSERT INTO task_services (name, job_id, price) SELECT *
                                                FROM tasks;

WITH tasks AS (
    SELECT
      'Exterior and interior cleaning' AS name,
      id,
      price
    FROM jobs
    WHERE has_interior_cleaning = TRUE
)
INSERT INTO task_services (name, job_id, price) SELECT *
                                                FROM tasks;


# --- !Downs

DROP TABLE IF EXISTS task_services;
DROP TABLE IF EXISTS accommodations_extras;
DROP TABLE IF EXISTS extras;
DROP TABLE IF EXISTS accommodations;