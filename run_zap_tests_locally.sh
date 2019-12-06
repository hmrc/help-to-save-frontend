#!/bin/bash

echo "Running zap browser tests"
./run_selenium_system_test.sh -e=local -b=chrome -t=@zap -p=true

sleep 5

echo "Running src.test.scala.runner.ZapRunner"
sbt "zap/test"