#!/usr/bin/env bash
sbt -Dbrowser=phantomjs -Dhost=http://localhost:7111 'test-only rnrb.suites.Runner'
