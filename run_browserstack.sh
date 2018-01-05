#!/bin/bash

username="marcus-blah"
key="get-from-browserstack.com"

# Provide the intended browser details when running, e.g.:
sbt -Dbrowser="browserstack" -Dbrowserstack.username=${username} -Dbrowserstack.key=${key} -Denvironment="local" -Dbrowserstack.os="android" -Dbrowserstack.os_version="7.0" -Dbrowserstack.browser="android" -Dbrowserstack.device="Samsung_Galaxy_S8" -Dbrowserstack.real_mobile="true" 'test-only hts.suites.RunnerSeleniumSystemTest'
