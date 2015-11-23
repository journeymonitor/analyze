test-keyspace:
	/usr/local/cassandra/bin/cqlsh -e "CREATE KEYSPACE IF NOT EXISTS test WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };"

travisci-after-success:
	[ "${TRAVIS_PULL_REQUEST}" = "false" ] && /bin/bash ./build/create-github-release.sh ${GITHUB_TOKEN} travisci-build-${TRAVIS_BRANCH}-${TRAVIS_BUILD_NUMBER} ${TRAVIS_COMMIT} https://travis-ci.org/journeymonitor/analyze/builds/${TRAVIS_BUILD_ID}
