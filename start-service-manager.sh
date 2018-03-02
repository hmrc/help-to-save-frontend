#!/usr/bin/env bash

ENVIRONMENT="local"
STOP_SERVICES="true"

echo "Environment = $ENVIRONMENT"
echo "Stop Services = $STOP_SERVICES"

cd ${WORKSPACE}
rm -rf service-manager-config
git clone git@github.com:hmrc/service-manager-config.git

if [ "$STOP_SERVICES" == "true" ]; then
echo "Stopping Services ..."
sm --stop ALL
sm --cleanlogs
fi


echo "Starting HTS dependencies from snapshots"
sm --start HTS_ALL -f --wait 1200 --noprogress
if [ $? != 0 ]
then
    echo "HTS dependencies failed to start"
    sm --stop HTS_ALL
    exit 1
fi