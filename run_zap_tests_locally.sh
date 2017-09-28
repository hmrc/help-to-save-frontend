#!/bin/bash

###################################################################
# You need to have ZAP installed and running locally via command: #
# zap.sh -daemon -config api.disableKey=true -port 11000          #
# from inside your zaproxy folder                                 #
# download and install ZAP from here:                             #
# https://github.com/zaproxy/zaproxy/wiki/Downloads               #
###################################################################

echo "Running zap browser tests"
./run_selenium_system_test.sh local zap-chrome @zap

sleep 5

echo "Running src.test.scala.runner.ZapRunner"
sbt zap:test