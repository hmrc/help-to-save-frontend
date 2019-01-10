#!/bin/bash

echo "Running zap browser tests"
./run_selenium_system_test.sh -e=local -b=zap-chrome -t=@zap

sleep 5

echo "Running src.test.scala.runner.ZapRunner"
sbt "zap/test"