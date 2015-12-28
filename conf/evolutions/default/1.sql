# --- !Ups

CREATE TABLE IF NOT EXISTS users (
  id              SERIAL PRIMARY KEY,
  created_date    TIMESTAMP    NOT NULL DEFAULT now(),
  updated_date    TIMESTAMP    NOT NULL DEFAULT now(),
  first_name      VARCHAR(150) NOT NULL,
  last_name       VARCHAR(150) NOT NULL,
  email           VARCHAR(255) UNIQUE,
  password        VARCHAR(255),
  salt            VARCHAR(255) NOT NULL,
  facebook_id     VARCHAR(100) UNIQUE,
  phone_code      VARCHAR(4)   NOT NULL,
  phone           VARCHAR(16)  NOT NULL,
  user_type       INTEGER      NOT NULL,
  verified        BOOLEAN      NOT NULL DEFAULT FALSE,
  code            VARCHAR(32),
  profile_picture VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS locations (
  id           SERIAL PRIMARY KEY,
  created_date TIMESTAMP    NOT NULL DEFAULT now(),
  updated_date TIMESTAMP    NOT NULL DEFAULT now(),
  name         VARCHAR(255) NOT NULL,
  country      VARCHAR(255) NOT NULL,
  state        VARCHAR(255),
  city         VARCHAR(255) NOT NULL,
  zip_code     VARCHAR(6)   NOT NULL,
  user_id      INT          NOT NULL REFERENCES users (id)
);

# --- !Downs

DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS locations CASCADE;