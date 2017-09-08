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
that you will need to download Selenium drivers from http://docs.seleniumhq.org/download/. The exact version of a driver
to be downloaded will depend on the version of the corresponding browser - the versions of the driver and browser must be
compatible. Mac users will have to rename the downloaded 'chromedriver' file to 'chromedriver_mac'.

The unit tests can be run by running
```
sbt test
```
This command will not run the Selenium tests.

Run the Selenium tests separately by executing:
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
./run_selenium_system_test.sh dev chrome /usr/local/bin/chromedriver           # (1) runs all selenium tests on the dev environment using chrome
./run_selenium_system_test.sh qa phantomjs /usr/local/bin/chromedriver wip     # (2) runs selenium scenarios tagged with the '@wip' tag on the
                                                                                #     QA environment using phantomJS
./run_selenium_system_test.sh dev phantomjs /usr/local/bin/chromedriver @wip   # (3) the same as (2)
./run_selenium_system_test.sh local chrome /usr/local/bin/chromedriver wip sit # (4) runs selenium scenarios tagged with either the '@wip' or '@sit'
                                                                                #     tags locally using chrome
```

If you wish to run the Selenium tests from Intellij, you'll need to:
1. Install the Cucumber for Java plugin.
2. In "Edit configurations" > "Cucumber java" > "VM options" enter, for example: -Dbrowser=chrome -Denvironment=dev -Ddrivers=/usr/local/bin
3. In "Edit configurations" > "Cucumber java" > "Glue" enter: hts.steps