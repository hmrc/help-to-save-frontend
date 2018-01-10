#!/usr/bin/env bash

echo "*********** Running service health check ***********"

echo "*********** Retrieving running services ***********"
SM_STATUS_OUTPUT=`sm -s`
echo $SM_STATUS_OUTPUT

echo "*********** Checking for services that are not running ***********"
SERVICES_STRING="$(sm -d | grep  HTS_ALL  | cut -d '>' -f 2 | tr -d '[:space:]')"
IFS=',' read -r -a SERVICES <<< "$SERVICES_STRING"
status=0
for SERVICE in "${SERVICES[@]}";
do
  echo $SM_STATUS_OUTPUT | grep -q $SERVICE
  if [ $? != 0 ]; then
    echo "$SERVICE failed status check"
    status=1
  fi
done

if [ $status != 0 ]; then
  echo "*********** HTS_ALL failed status check ***********"
else
  echo "*********** HTS_ALL passed status check ***********"
fi

exit $status
