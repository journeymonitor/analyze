#!/bin/bash
curl -XPOST -s -H "Authorization: token $1" -H "Content-Type: application/json" --data \{\"tag_name\":\"$2\",\"target_commitish\":\"$3\",\"body\":\"$4\"\} https://api.github.com/repos/journeymonitor/analyze/releases
