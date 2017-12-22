#!/usr/bin/env bash

export DISPLAY=${DISPLAY=":99"}

sh ./run_browser_dependencies.sh

export ARGS="-Denvironment=local -Dbrowser=browserstack"

sbt $ARGS -DtestDevice=BS_Sierra_Chrome_55 clean 'test-only uk.gov.hmrc.integration.cucumber.utils.RunnerBrowserStackTests'