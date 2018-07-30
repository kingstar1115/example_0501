# --- !Ups

CREATE TABLE countries (
  id           SERIAL PRIMARY KEY,
  created_date TIMESTAMP    NOT NULL DEFAULT now(),
  name         VARCHAR(100) NOT NULL,
  code         VARCHAR(100) NOT NULL UNIQUE,
  "default"    BOOLEAN      NOT NULL DEFAULT false
);

INSERT INTO countries (name, code ,"default") VALUES ('San Mateo County', 'sanMateo' ,true);
INSERT INTO countries (name, code) VALUES ('Santa Clara County', 'santaClara');
INSERT INTO countries (name, code) VALUES ('San Francisco County', 'sanFrancisco');

ALTER TABLE day_slots
  DROP CONSTRAINT day_slots_date_key,
  ADD COLUMN country_id INT REFERENCES countries (id);

UPDATE day_slots
SET country_id = 1;

ALTER TABLE day_slots
  ALTER COLUMN country_id SET NOT NULL,
  ADD CONSTRAINT day_slots_date_country_id_unique UNIQUE (date, country_id);

CREATE TABLE zip_codes (
  id           SERIAL PRIMARY KEY,
  created_date TIMESTAMP  NOT NULL DEFAULT now(),
  zip_code     VARCHAR(6) NOT NULL,
  country_id   INT        NOT NULL REFERENCES countries (id)
);

INSERT INTO zip_codes (zip_code, country_id) VALUES ('94002', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94003', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94005', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94010', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94011', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94012', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94014', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94015', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94016', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94017', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94018', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94019', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94020', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94021', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94025', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94026', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94027', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94028', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94029', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94030', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94031', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94037', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94038', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94044', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94045', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94059', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94060', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94061', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94062', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94063', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94064', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94065', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94066', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94067', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94070', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94071', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94074', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94080', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94083', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94096', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94098', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94099', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94128', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94307', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94308', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94401', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94402', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94403', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94404', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94405', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94406', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94407', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94408', 1);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94409', 1);

INSERT INTO zip_codes (zip_code, country_id) VALUES ('94022', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94023', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94024', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94035', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94039', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94040', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94041', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94042', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94043', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94085', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94086', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94087', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94088', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94089', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94090', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94301', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94302', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94303', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94304', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94305', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94306', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94309', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94310', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95002', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95008', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95009', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95011', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95013', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95014', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95015', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95020', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95021', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95026', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95030', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95031', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95032', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95035', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95036', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95037', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95038', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95042', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95044', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95046', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95050', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95051', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95052', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95054', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95055', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95056', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95070', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95071', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95101', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95102', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95103', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95106', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95108', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95109', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95110', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95111', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95112', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95113', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95114', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95115', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95116', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95117', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95118', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95119', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95120', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95121', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95122', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95123', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95124', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95125', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95126', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95127', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95128', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95129', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95130', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95131', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95132', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95133', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95134', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95135', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95136', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95137', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95138', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95139', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95140', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95141', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95142', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95148', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95150', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95151', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95152', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95154', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95155', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95156', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95157', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95158', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95159', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95160', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95161', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95164', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95170', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95171', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95172', 2);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('95173', 2);

INSERT INTO zip_codes (zip_code, country_id) VALUES ('94016', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94102', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94103', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94104', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94105', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94107', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94108', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94109', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94110', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94111', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94112', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94114', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94115', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94116', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94117', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94118', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94119', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94120', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94121', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94122', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94123', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94124', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94125', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94126', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94127', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94129', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94130', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94131', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94132', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94133', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94134', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94137', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94139', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94140', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94141', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94142', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94143', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94144', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94145', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94146', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94147', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94151', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94153', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94154', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94156', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94158', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94159', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94160', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94161', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94162', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94163', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94164', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94171', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94172', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94177', 3);
INSERT INTO zip_codes (zip_code, country_id) VALUES ('94188', 3);