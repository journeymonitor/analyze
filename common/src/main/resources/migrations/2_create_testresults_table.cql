-- description: create testresults table
-- authoredAt: 1447272601000
-- up:

CREATE TABLE testresults (
    testcase_id text,
    testresult_id text,
    datetime_run timestamp,
    har text,
    is_analyzed boolean,
    PRIMARY KEY ((testcase_id), datetime_run)
) WITH CLUSTERING ORDER BY (datetime_run DESC);

-- down:

DROP TABLE testresults;
