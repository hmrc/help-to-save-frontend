#! /bin/sh

exit_if_status_1() {
  if [ $? = 1 ]
  then
     exit 1
  fi
}

echo "Starting Service Manager with HTS_DIGITAL_SELENIUM"
./start-service-manager.sh
exit_if_status_1

./services-health-check.sh
exit_if_status_1

echo "./run_selenium_system_test.sh -e=local -b=chrome -d=/usr/local/bin/chromedriver"
./run_selenium_system_test.sh -e=local -b=chrome -d=/usr/local/bin/chromedriver
EXIT_CODE=$?
echo "EXIT_CODE = $EXIT_CODE"


./output-log-exceptions.sh

sm --stop HTS_DIGITAL_SELENIUM
exit $EXIT_CODE