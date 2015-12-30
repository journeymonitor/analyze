-- description: create statistics table
-- authoredAt: 1447272602000
-- up:

CREATE TABLE statistics (
    testcase_id text,
    testresult_id text,
    datetime_run timestamp,
    runtime_milliseconds int,
    number_of_200 int,
    number_of_400 int,
    number_of_500 int,
    PRIMARY KEY ((testcase_id), datetime_run)
) WITH CLUSTERING ORDER BY (datetime_run DESC);

-- down:

DROP TABLE statistics;
