# --- !Ups

CREATE TABLE IF NOT EXISTS users (
  id              SERIAL PRIMARY KEY,
  created_date    TIMESTAMP           NOT NULL DEFAULT now(),
  first_name      VARCHAR(150)        NOT NULL,
  last_name       VARCHAR(150)        NOT NULL,
  email           VARCHAR(255) UNIQUE NOT NULL,
  password        VARCHAR(255),
  salt            VARCHAR(255)        NOT NULL,
  facebook_id     VARCHAR(100) UNIQUE,
  phone_code      VARCHAR(4)          NOT NULL,
  phone           VARCHAR(16)         NOT NULL,
  user_type       INTEGER             NOT NULL,
  verified        BOOLEAN             NOT NULL DEFAULT FALSE,
  code            VARCHAR(32),
  profile_picture TEXT,
  stripe_id       VARCHAR(32),
  payment_method  VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS locations (
  id                SERIAL PRIMARY KEY,
  created_date      TIMESTAMP    NOT NULL DEFAULT now(),
  title             VARCHAR(255) NOT NULL,
  address           VARCHAR(255),
  formatted_address VARCHAR(255) NOT NULL,
  latitude          NUMERIC      NOT NULL,
  longitude         NUMERIC      NOT NULL,
  notes             TEXT,
  zip_code          VARCHAR(6),
  apartments        VARCHAR(10),
  user_id           INT          NOT NULL REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS agents (
  id           SERIAL PRIMARY KEY,
  created_date TIMESTAMP    NOT NULL DEFAULT now(),
  fleet_id     BIGINT       NOT NULL UNIQUE,
  name         VARCHAR(255) NOT NULL,
  fleet_image  TEXT         NOT NULL,
  phone        VARCHAR(20)  NOT NULL
);

CREATE TABLE IF NOT EXISTS vehicles (
  id              SERIAL PRIMARY KEY,
  created_date    TIMESTAMP    NOT NULL DEFAULT now(),
  maker_id        INT          NOT NULL,
  maker_nice_name VARCHAR(150) NOT NULL,
  model_id        VARCHAR(255) NOT NULL,
  model_nice_name VARCHAR(255) NOT NULL,
  year_id         INT          NOT NULL,
  year            INT          NOT NULL,
  color           VARCHAR(255) NOT NULL DEFAULT 'None',
  lic_plate       VARCHAR(255),
  user_id         INT          NOT NULL REFERENCES users (id),
  deleted         BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS jobs (
  id                    SERIAL PRIMARY KEY,
  created_date          TIMESTAMP    NOT NULL DEFAULT now(),
  job_id                BIGINT       NOT NULL UNIQUE,
  job_status            INT          NOT NULL DEFAULT 6,
  scheduled_time        TIMESTAMP    NOT NULL,
  images                TEXT,
  submitted             BOOLEAN      NOT NULL DEFAULT FALSE,
  user_id               INT          NOT NULL REFERENCES users (id),
  agent_id              INT REFERENCES agents (id),
  vehicle_id            INT          NOT NULL REFERENCES vehicles (id),
  job_address           VARCHAR(255),
  job_pickup_phone      VARCHAR(20),
  customer_phone        VARCHAR(20),
  team_name             VARCHAR(255),
  has_interior_cleaning BOOLEAN NOT NULL,
  latitude              NUMERIC      NOT NULL,
  longitude             NUMERIC      NOT NULL,
  payment_method        VARCHAR(32)  NOT NULL,
  price                 INT NOT NULL,
  tip                   INT          NOT NULL DEFAULT 0,
  promotion             INT          NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS settings (
  id    SERIAL PRIMARY KEY,
  key   VARCHAR(32) NOT NULL,
  value VARCHAR(64) NOT NULL
)

  # --- !Downs
DROP TABLE IF EXISTS jobs;
DROP TABLE IF EXISTS vehicles;
DROP TABLE IF EXISTS agents;
DROP TABLE IF EXISTS locations;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS settings;
