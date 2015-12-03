# --- !Ups

CREATE TABLE IF NOT EXISTS users (
  id           SERIAL PRIMARY KEY,
  created_date TIMESTAMP    NOT NULL DEFAULT now(),
  updated_date TIMESTAMP    NOT NULL DEFAULT now(),
  first_name   VARCHAR(150) NOT NULL,
  last_name    VARCHAR(150) NOT NULL,
  email        VARCHAR(255),
  password     VARCHAR(255),
  salt         VARCHAR(255),
  verify_code  INT,
  facebook_id  BIGINT,
  phone        VARCHAR(16)  NOT NULL,
  user_type    INTEGER      NOT NULL,
  verified     BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS locations (
  id           SERIAL PRIMARY KEY,
  created_date TIMESTAMP    NOT NULL DEFAULT now(),
  updated_date TIMESTAMP    NOT NULL DEFAULT now(),
  name         VARCHAR(255) NOT NULL,
  country      VARCHAR(255) NOT NULL,
  state        VARCHAR(255),
  city         VARCHAR(255) NOT NULL,
  zip_code     INT          NOT NULL,
  user_id      INT          NOT NULL REFERENCES users (id)
);

# --- !Downs

DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS locations;