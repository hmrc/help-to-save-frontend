#!/bin/bash
sbt -Dbrowser=chrome -Denvironment=dev 'selenium:test-only hts.suites.RunnerSeleniumSystemTest'
