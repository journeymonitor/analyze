-- description: recreate testresults table
-- authoredAt: 1447272604000
-- up:

CREATE TABLE testresults (
    testcase_id text,
    testresult_id text,
    datetime_run timestamp,
    har text,
    is_analyzed boolean,
    PRIMARY KEY (testresult_id)
);

-- down:

DROP TABLE testresults;
