#!/usr/bin/env bash

export DISPLAY=${DISPLAY=":99"}

sh ./run_browser_dependencies.sh

export ARGS="-Denvironment=local -Dbrowser=browserstack"

sbt $ARGS -DtestDevice=BS_Win7_IE_11 clean 'test-only hts.suites.RunnerBrowserStackTests'