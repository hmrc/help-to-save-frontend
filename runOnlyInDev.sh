#!/bin/bash
sbt -Dbrowser=chrome -Dwebdriver.chrome.driver=/usr/local/bin/chromedriver -Denvironment=dev 'selenium:test-only hts.suites.RunnerSeleniumSystemTest'
