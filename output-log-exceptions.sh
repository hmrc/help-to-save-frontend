#!/usr/bin/env bash

function printExceptionsFromLog {
    echo "Printing log exception for:" $1
    sm --logs $1 | grep -i '^[[:space:]]*at' --before-context=7
}


SERVICES_STRING="$(sm -d | grep  HTS_DIGITAL_SELENIUM  | cut -d '>' -f 2 | tr -d '[:space:]')"
IFS=',' read -r -a SERVICES <<< "$SERVICES_STRING"

for SERVICE in "${SERVICES[@]}";
 do
    printExceptionsFromLog $SERVICE
  done
