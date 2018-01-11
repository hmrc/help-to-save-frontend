#! /bin/sh

echo "Starting Service Manager with HTS_ALL"
./start-service-manager.sh

echo "Starting integration tests"
sbt it:test

EXIT_CODE=$?
echo "EXIT_CODE = $EXIT_CODE"

./services-health-check.sh
./output-log-exceptions.sh

sm --stop HTS_ALL
exit $EXIT_CODE