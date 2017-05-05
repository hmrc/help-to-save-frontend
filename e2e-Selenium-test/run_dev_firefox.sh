#!/usr/bin/env bash
sbt -Dbrowser=firefox -Dhost=https://www-dev.tax.service.gov.uk 'test-only hts.suites.Runner'
