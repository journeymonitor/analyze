-- description: recreate statistics table with daybucket
-- authoredAt: 1455718967000
-- up:

CREATE TABLE statistics (
    testcase_id text,
    day_bucket text,
    testresult_datetime_run timestamp,
    testresult_id text,
    number_of_200 int,
    number_of_400 int,
    number_of_500 int,
    runtime_milliseconds int,
    PRIMARY KEY ((testcase_id, day_bucket), testresult_datetime_run)
) WITH CLUSTERING ORDER BY (testresult_datetime_run DESC);

-- down:

DROP TABLE statistics;
