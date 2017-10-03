#!/bin/bash

echo "Running zap browser tests"
./run_selenium_system_test.sh local zap-chrome @zap

sleep 5

echo "Running src.test.scala.runner.ZapRunner"
sbt zap:test