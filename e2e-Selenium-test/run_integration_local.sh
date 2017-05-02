#!/bin/bash
sbt -Dbrowser=chrome -Denvironment=local 'test-only uk.gov.hmrc.integration.cucumber.utils.Runner'


