# help-to-save-frontend 

[![Build Status](https://travis-ci.org/hmrc/help-to-save-frontend.svg)](https://travis-ci.org/hmrc/help-to-save-frontend) [ ![Download](https://api.bintray.com/packages/hmrc/releases/help-to-save-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/help-to-save-frontend/_latestVersion)

## Help to Save Frontend

Frontend for application process for Help to Save. Runs on port 7000 when started locally by the service manager.

Start service manager with the following dependencies:

```
sm --start DATASTREAM CA_FRONTEND ASSETS_FRONTEND AUTH_LOGIN_STUB AUTH_LOGIN_API GG_AUTHENTICATION  GG GG_STUBS GG_AUTHENTICATION USER_DETAILS AUTH IDENTITY_VERIFICATION_STUB HELP_TO_SAVE ENROLMENT_EXCEPTION_LIST IDENTITY_VERIFICATION CITIZEN_DETAILS IDENTITY_VERIFICATION_FRONTEND -f 
```

## Testing
Selenium system tests are distinguished from unit tests by having `SeleniumSystemTest` in the relevant runner name. Note
that you will need to download Selenium drivers from http://docs.seleniumhq.org/download/. Mac users will have to rename
the downloaded 'chromedriver' file to 'chromedriver_mac'.

The unit tests can be run by running
```
sbt test
```
This command will not run the Selenium tests.

The Selenium tests can be run separately by running 
 ```
 ./run_selenium_system_test.sh ${ENV} ${BROWSER} ${DRIVERS}
```
where `${ENV}` indicates the environment the tests should run on (one of `dev`, `qa` or `local`), `${BROWSER}` is
the browser the tests should run on (one of `chrome` or `phantomjs`) and `${DRIVERS}` is the path to the folder
containing the Selenium driver files. This command will not run the unit tests. To run only a subset of
Selenium scenarios, tag the relevant scenarios and then run the command
 ```
 ./run_selenium_system_test.sh ${ENV} ${BROWSER} ${DRIVERS} ${TAGS}
 ```
where `${TAGS}` is a space separated list containing the relevant tags. Examples:

```
./run_selenium_system_test.sh dev chrome selenium-system-test/drivers           # (1) runs all selenium tests on the dev environment using chrome
./run_selenium_system_test.sh qa phantomjs selenium-system-test/drivers wip     # (2) runs selenium scenarios tagged with the '@wip' tag on the
                                                                                #     QA environment using phantomJS
./run_selenium_system_test.sh dev phantomjs selenium-system-test/drivers @wip   # (3) the same as (2)
./run_selenium_system_test.sh local chrome selenium-system-test/drivers wip sit # (4) runs selenium scenarios tagged with either the '@wip' or '@sit'
                                                                                #     tags locally using chrome
```
