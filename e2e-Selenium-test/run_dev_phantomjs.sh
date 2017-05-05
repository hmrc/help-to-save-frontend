#!/usr/bin/env bash
sbt -Dbrowser=phantomjs -Dhost=https://www-dev.tax.service.gov.uk 'test-only hts.suites.Runner'
