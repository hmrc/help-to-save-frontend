#!/usr/bin/env bash
sbt -Dbrowser=chrome -Dhost=https://www-dev.tax.service.gov.uk 'test-only hts.suites.Runner_WIP'
