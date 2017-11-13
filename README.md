# help-to-save-frontend

[![Build Status](https://travis-ci.org/hmrc/help-to-save-frontend.svg)](https://travis-ci.org/hmrc/help-to-save-frontend) [ ![Download](https://api.bintray.com/packages/hmrc/releases/help-to-save-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/help-to-save-frontend/_latestVersion)

## Help to Save Frontend

Frontend for application process for Help to Save. Runs on port 7000 when started locally by the service manager.

Start service manager with the following dependencies:

```
sm --start DATASTREAM CA_FRONTEND ASSETS_FRONTEND AUTH_LOGIN_STUB AUTH_LOGIN_API GG_AUTHENTICATION  GG GG_STUBS GG_AUTHENTICATION USER_DETAILS AUTH IDENTITY_VERIFICATION_STUB HELP_TO_SAVE ENROLMENT_EXCEPTION_LIST IDENTITY_VERIFICATION CITIZEN_DETAILS IDENTITY_VERIFICATION_FRONTEND -f 
```

##Main Public API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/apply-for-help-to-save`                         |        GET        | Redirects the user to the about-help-to-save page|
|`/help-to-save/apply-for-help-to-save/about-help-to-save`      |        GET        | Displays the about-help-to-page|
|'/help-to-save/apply-for-help-to-save/eligibility'             |        GET        | Displays information about Eligibility for help-to-save|
|'/help-to-save/apply-for-help-to-save/how-the-account-works'   |        GET        | Displays information about how help-to-save works|
|'/help-to-save/apply-for-help-to-save/how-we-calculate-bonuses'|        GET        | Displays information about bonuses
|'/help-to-save/apply-for-help-to-save/apply'                   |        GET        | Displays information about how to apply for help-to-save|

##Register API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/register/create-an-account'                     |        GET        | Directs the user to the help-to-save create an account page|
|'/help-to-save/register/create-an-account'                     |        POST       | Submits an eligible user's details to create an account|
|'/help-to-save/register/user-cap-reached'                      |        GET        | Displays a page when user cap has been reached (Private Beta Only)|
|'/help-to-save/register/give-email'                            |        GET        | Directs the user to a page where they can supply a new email address|
|'/help-to-save/register/give-email-submit'                     |        POST       | Submits an eligible user's new email address to the verification service|
|'/help-to-save/select-email'                                   |        GET        | Page where user selects existing or new email address to use for help-to-save|
|'/help-to-save/register/select-email-submit'                   |        POST       | If given, submits a new email to the email verification service. Otherwise continues the create account journey|
|'/help-to-save/details-are-incorrect'                          |        GET        | Page showing user what to do if the details shown are incorrect|

##IV API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/iv/journey-result'                              |        GET        | The continue URL we pass to Auth to return users after IV|

##New Applicant Email Address Update API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/register/verify-email/:email'                   |        GET        | Submits the users new email address to the email verification service|
|'/help-to-save/register/email-verified'                        |        GET        | Users are redirected here after they have verified their email|
|'/help-to-save/register/email-updated'                         |        GET        | Page showing user their email address was successfully updated for help-to-save|


##Eligibility Check API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/check-eligibility'                              |        GET        | Endpoint to trigger user eligibility check on DES|
|`/help-to-save/not-eligible'                                   |        GET        | Displays a page to the user showing they are not eligible|
|`/help-to-save/eligible'                                       |        GET        | Displays a page to the user showing they are eligible|


##Access Account API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/access-account'                                 |        GET        | Redirects the user to their account page on NSI if they have an account|

##Account Holder Update Email Address API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/account/update-your-email-address'              |        GET        | Directs an account holder to a page for them to enter a new email address|
|`/help-to-save/account/verify-email'                           |        POST       | Submits the account holders new email address to the email verification service|
|'/help-to-save/account/email-verified'                         |        GET        | Account holders are redirected here after they have verified their email|
|'/help-to-save/account/email-updated'                          |        GET        | Page showing account holder their email address was successfully updated for help-to-save|
|`/help-to-save/account/email-update-error'                     |        GET        | Page showing account holder their email update request has been unsuccessful|

##IP Whitelisting API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/forbidden'                                      |        GET        | Page shown if the user is not whitelisted if IP-whitelisting is enabled|


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

Only if you want to run the Selenium tests locally, execute the following commands to start the relevant services:

sm --start HTS_DEP -f

cd <front end path>
sbt "run 7000"

cd <back end path>
sbt "run 7001"

cd <stub path>
sbt "run 7002"

Then (to run against any environment) execute:
 ```
 ./run_selenium_system_test.sh ${ENV} ${BROWSER} ${DRIVERS}
```
where `${ENV}` indicates the environment the tests should run on (one of `dev`, `qa` or `local`), `${BROWSER}` is
the browser the tests should run on `chrome` and `${DRIVERS}` is the path to the folder
containing the Selenium driver files. This command will not run the unit tests. To run only a subset of
Selenium scenarios, tag the relevant scenarios and then run the command
 ```
 ./run_selenium_system_test.sh ${ENV} ${BROWSER} ${DRIVERS} ${TAGS}
 ```
where `${TAGS}` is a space separated list containing the relevant tags. Examples:

```
./run_selenium_system_test.sh dev chrome /usr/local/bin/chromedriver           # (1) runs all selenium tests on the dev environment using chrome
./run_selenium_system_test.sh qa chrome /usr/local/bin/chromedriver wip     # (2) runs selenium scenarios tagged with the '@wip' tag on the
                                                                                #     QA environment using chrome
./run_selenium_system_test.sh dev chrome /usr/local/bin/chromedriver @wip   # (3) the same as (2)
./run_selenium_system_test.sh local chrome /usr/local/bin/chromedriver wip sit # (4) runs selenium scenarios tagged with either the '@wip' or '@sit'
                                                                                #     tags locally using chrome
```

If you wish to run the Selenium tests from Intellij, you'll need to:
1. Install the Cucumber for Java plugin.
2. In "Edit configurations" > "Cucumber java" > "VM options" enter, for example: -Dbrowser=chrome -Denvironment=dev -Ddrivers=/usr/local/bin
3. In "Edit configurations" > "Cucumber java" > "Glue" enter: hts.steps


##ZAP (pen testing)

You need to have ZAP installed and running locally via command:
zap.sh -daemon -config api.disableKey=true -port 11000
from inside your zaproxy folder
download and install ZAP from here:
https://github.com/zaproxy/zaproxy/wiki/Downloads
