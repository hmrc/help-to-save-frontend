#! /bin/sh

echo "./run_selenium_system_test.sh local zap-headless /usr/local/bin/chromedriver"
./run_selenium_system_test.sh local zap-headless /usr/local/bin/chromedriver
EXIT_CODE=$?
echo "EXIT_CODE = $EXIT_CODE"

./services-health-check.sh
./output-log-exceptions.sh

exit $EXIT_CODE