-- description: drop testresults table
-- authoredAt: 1447272603000
-- up:

DROP TABLE testresults;

-- down:

CREATE TABLE testresults (
    testcase_id text,
    testresult_id text,
    datetime_run timestamp,
    har text,
    is_analyzed boolean,
    PRIMARY KEY ((testcase_id), datetime_run)
) WITH CLUSTERING ORDER BY (datetime_run DESC);
