#!/bin/bash
sbt -Dbrowser=chrome -Denvironment=local 'test-only hmrc.utils.Runner'


