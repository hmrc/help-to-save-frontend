#!/bin/bash
sbt -Dbrowser=chrome -Denvironment=dev 'test-only "src.test.scala.hts.suites.RunnerSeleniumSystemTest"'
