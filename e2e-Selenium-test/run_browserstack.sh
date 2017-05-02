#!/usr/bin/env bash

sbt -Dbrowser=browserstack -DtestDevice=BS_Win7_Chrome_47 -Denvironment=local -- -n 'test-only uk.gov.hmrc.integration.cucumber.utils.RunnerBrowserStackTests'