#! /bin/sh

echo "Starting Service Manager with HTS_ALL"
./start-service-manager.sh
if [ $? = 1 ]
then
    exit
fi

echo "./run_selenium_system_test.sh local chrome /usr/local/bin/chromedriver"
./run_selenium_system_test.sh local chrome /usr/local/bin/chromedriver
EXIT_CODE=$?
echo "EXIT_CODE = $EXIT_CODE"

./services-health-check.sh
./output-log-exceptions.sh

sm --stop HTS_ALL
exit $EXIT_CODE