#!/bin/bash

if !(hash docker 2>/dev/null); then
    echo "Failed: Please install docker"
    echo "https://docs.docker.com/engine/installation/linux/ubuntu/#install-docker"
    exit 1
fi

docker pull owasp/zap2docker-stable

if !(docker ps | grep -q owasp/zap2docker); then
    echo "Starting Docker"
    DOCKERID=`docker run -d --net="host" -u zap -p 11000:11000 owasp/zap2docker-stable zap-x.sh -daemon -config api.disablekey=true -port 11000`
    echo $DOCKERID
else
    echo "Using existing docker"
    DOCKERID=`docker container ps | grep zap | awk -F ' ' '{ print $NF }'`
    echo $DOCKERID
fi


echo "Running tests"
sbt -Dbrowser=zap-chrome -Denvironment=Local 'test-only suites.RunZapTests'

sleep 5

echo "Running Zap Tests"
sbt "testOnly ZapRunner"


echo "Killing ZAP"

docker stop $DOCKERID

