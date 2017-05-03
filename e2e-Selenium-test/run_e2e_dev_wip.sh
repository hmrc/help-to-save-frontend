#!/bin/bash
sbt -Dbrowser=chrome -Denvironment=dev 'test-only hmrc.utils.RunnerWip'


