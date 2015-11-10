-- description: create things table
-- authoredAt: 1447165714000
-- up:

CREATE TABLE things (
  id int,
  name text,
  PRIMARY KEY (id)
);

-- down:

DROP TABLE things;
