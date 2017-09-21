#!/bin/bash

echo "Running zap browser tests"
#sbt -Dbrowser=zap-chrome -Denvironment=Local 'test-only hts.suites.RunZapTests'
./run_selenium_system_test.sh local zap-chrome @zap

sleep 5

echo "Running Zap Tests"
sbt "testOnly ZapRunner"