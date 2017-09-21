#!/bin/bash

echo "Running browser tests"
sbt -Dbrowser=zap-chrome -Denvironment=Local 'test-only hts.suites.RunZapTests'

sleep 5

echo "Running Zap Tests"
sbt "testOnly ZapRunner"