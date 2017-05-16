# help-to-save-frontend

[![Build Status](https://travis-ci.org/hmrc/help-to-save-frontend.svg)](https://travis-ci.org/hmrc/help-to-save-frontend) [ ![Download](https://api.bintray.com/packages/hmrc/releases/help-to-save-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/help-to-save-frontend/_latestVersion)

## Help to Save Frontend

Frontend for application process for Help to Save. Runs on port 7000 when started locally by the service manager.

## Testing
Selenium system tests are distinguished from unit tests by having `SeleniumSystemTest` in the relevant runner name. Selenium
runners which are marked as WIP should contain string `WIP` in their name in addition to ending with `SeleniumSystemTest`.

The unit tests can be run by running
```
sbt test
```
This command will not run the Selenium tests.

The Selenium tests can be run separately by running
 ```
 sbt -Dhost=${HOST} -Dbrowser=${BROWSER} 'selenium:test'
```
where `${HOST}` is the host of thee application under test (e.g. `http://localhost:7000`) and
`${BROWSER}` is one of `chrome` or `phantomjs`. This command will not run the unit tests. For Selenium
tests marked as `WIP` run
 ```
 sbt -Dhost=${HOST} -Dbrowser=${BROWSER} 'selenium-wip:test'
```
instead. The Selenium tests can also be run using the `run_selenium_system_test.sh` script
provided (see script for details). Here's an example:

./run_selenium_system_test.sh dev phantomjs
