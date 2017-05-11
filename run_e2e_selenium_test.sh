#!/bin/bash

USAGE="[env] [browser] where env=dev|qa|local, browser=chrome|phantomjs"

echo $3

if [ "$1" != 'dev' ] && [ "$1" != 'qa' ] && [ "$1" != 'local' ] || ([ "$2" != 'chrome' ] && [ "$2" != 'phantomjs' ])
then
  echo "Expected usage:"${USAGE}
  exit 1
fi

if [ -z $3 ]
then
 TAG=''
else
 TAG=$3
fi

if [ "$1" == 'dev' ]
then
  HOST='https://www-dev.tax.service.gov.uk'
elif [ "$1" == 'qa' ]
then
  HOST='https://www-qa.tax.service.gov.uk'
else
  HOST='https://localhost:7000'
fi

if [ "$TAG" == 'WIP' ]
then
  sbt -Dhost=${HOST} -Dbrowser=$2 'selenium-wip:test'
else
  sbt -Dhost=${HOST} -Dbrowser=$2 'selenium:test'
fi