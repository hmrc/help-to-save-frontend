help-to-save-frontend
=====================

Frontend microservice which handles requests from the browser in the public digital Help to Save journey.

Table of Contents
=================

* [About Help to Save](#about-help-to-save)
* [The Public Digital Journey](#the-public-digital-journey)
* [Running and Testing](#running-and-testing)
   * [Running](#running)
   * [Unit tests](#unit-tests)
   * [Selenium tests](#selenium-tests)
   * [BrowserStack (cross-browser/-platform compatibility testing)](#browserstack-cross-browser-platform-compatibility-testing)
   * [ZAP (pen testing)](#zap-pen-testing)
* [Endpoints](#endpoints)
   * [Main Public API](#main-public-api)
   * [Eligibility Check API](#eligibility-check-api)
   * [Register API](#register-api)
   * [IV API](#iv-api)
   * [New Applicant Email Address Update API](#new-applicant-email-address-update-api)
   * [Access Account API](#access-account-api)
   * [Account Holder Update Email Address API](#account-holder-update-email-address-api)
   * [Close Account API](#close-account-api)
   * [IP Whitelisting API](#ip-whitelisting-api)
   * [Privacy Statement](#privacy-statement)
* [License](#license)

About Help to Save
==================
Please click [here](https://github.com/hmrc/help-to-save#about-help-to-save) for more information.


The Public Digital Journey
==========================
To request a HTS account, customers will access a HTS landing page hosted by GOV.UK. Once authenticated via Government Gateway, 
the customer will access the HTS Service whereby the customer may check their eligibility for HTS via DES API #2A. This collates 
responses from HoD’s NTC (for WTC), ITMP (for WTC) and DWP (for UC) to work out a person's eligibility for HTS.

If a customer is eligible for HTS they are able to continue the journey and create a Help To Save account by clicking a button. This
invokes an NS&I API to create a HTS account. Upon account creation the user is redirected to NS&I’s Help To Save web portal frontend.
At this time, in parallel, the HTS service invokes DES API #4a to set the HTS status flag in ITMP.

Once the customer has created an account and lands at NS&I, they will from then on be redirected on login from either party back to the
NS&I account home page. At this point, NS&I may redirect to other internal NS&I pages depending on the customer’s status.

When at NS&I, the customer will only be redirected back to the MDTP frontend microservice in the following scenarios:
- if the HMRC/MDTP Auth session expires after 4hrs.
- when they click the “Change Email” link which redirects to the service Change Email endpoint.
- when they click the “Feedback” link which redirects to HMRC Deskpro HtS team.
- when they click the “Sign Out” link which redirects to an anonymous HMRC Exit Survey.


Running and Testing
===================

Running
-------

Run `sbt run` on the terminal to start the service. The service runs on port 7000 by default.

Unit tests
----------
Run `sbt test` on the terminal to run the unit tests.


Selenium tests
--------------
Selenium system tests are distinguished from unit tests by having `SeleniumSystemTest` in the relevant runner name. Note
that you will need to download Selenium drivers from [here](http://docs.seleniumhq.org/download/). The exact version of a driver
to be downloaded will depend on the version of the corresponding browser - the versions of the driver and browser must be
compatible. Mac users will have to rename the downloaded `chromedriver` file to `chromedriver_mac`.


If you want to run the Selenium tests locally, execute the following command to start the relevant services:
```
sm --start HTS_ALL -f
```
Then execute:
 ```
 ./run_selenium_system_test.sh -e=${ENV} -b=${BROWSER} -d=${DRIVERS} -r=${rootUrl}
```
where `${ENV}` indicates the environment the tests should run on (one of `dev`, `qa` or `local`), `${BROWSER}` is
the browser the tests should run on (e.g. `chrome`) and `${DRIVERS}` is the path to the folder
containing the Selenium driver files. This command will not run the unit tests. To run only a subset of
Selenium scenarios, tag the relevant scenarios and then run the command
 ```
 ./run_selenium_system_test.sh -e=${ENV} -b=${BROWSER} -d=${DRIVERS} -r=${rootUrl} -t=${TAGS}
 ```
where `${TAGS}` is a comma separated list containing the relevant tags. Examples:

```
# (1) runs all selenium tests on the dev environment using chrome
./run_selenium_system_test.sh \
    -e=dev \
    -b=chrome \
    -d=/usr/local/bin/chromedriver \
    -r={mdtp dev host url}

# (2) runs selenium scenarios tagged with the `@wip` tag on the QA environment using chrome                 
./run_selenium_system_test.sh \
    -e=qa \
    -b=chrome \
    -d=/usr/local/bin/chromedriver \
    -r={mdtp qa host url} \
    -t="wip"

# (3) the same as (2)        
./run_selenium_system_test.sh \
    -e=dev \
    -b=chrome \
    -d=/usr/local/bin/chromedriver \
    -r={mdtp dev host url} \
    -t="wip"       

# (4) runs selenium scenarios tagged with either the `@wip` or `@sit` tags locally using chrome
./run_selenium_system_test.sh \
    -e=local \
    -b=chrome \
    -d=/usr/local/bin/chromedriver \
    -t="wip,sit" 
```

If you wish to run the Selenium tests from Intellij, you`ll need to:
1. Install the Cucumber for Java plugin.
2. In "Edit configurations" > "Cucumber java" > "VM options" enter, for example: -Dbrowser=chrome -Denvironment=dev -Ddrivers=/usr/local/bin
3. In "Edit configurations" > "Cucumber java" > "Glue" enter: hts.steps

BrowserStack (cross-browser/-platform compatibility testing)
------------------------------------------------------------
NOTE: BrowserStack has compatibility issues with Safari accessing localhost, so please run tests against Safari 
MANUALLY at [browserstack](https://www.browserstack.com/start). All iOS devices run Safari by default.

To run parallel tests using BrowserStack in AUTOMATED mode, you need to:
1. If necessary, start mongod using ```sudo service mongod start```
2. Start service manager using ```sm --start HTS_ALL -f```
3. Run the BrowserStackLocal file in this project up to four times using:

```
./BrowserStackLocal --key {KEY} --local-identifier {1/2/3/4} # (the script is configured to accept either no local identifier or just 1/2/3/4)
```

4. Once you have the desired number of BrowserStack instances running locally,
   configure the run script script to use the desired OS/Browser combination by
   replacing the placeholders with the values listed [here](https://www.browserstack.com/automate/capabilities) or
   you can visit the spreadsheet [here](https://docs.google.com/spreadsheets/d/1xa9h-A7goX3yOd954Oo2sdE2KNUsAWpWa6VFCXHKt-g/edit#gid=0).
   For example, You can run commands such as:

        
```
./run_selenium_system_test.sh \
    -e=local \
    -b=browserstack{1/2/3/4} \
    -d=BrowserStackLocal \
    -j="-Dbrowserstack.os={OS},-Dbrowserstack.os_version="{OS_Version}",-Dbrowserstack.device={device},-Dbrowserstack.real_mobile=true,-Dbrowserstack.username={USERNAME},-Dbrowserstack.key={KEY}" \
    -t=@BrowserStack
./run_selenium_system_test.sh \
    -e=local \
    -b=browserstack{1/2/3/4} \
    -d=BrowserStackLocal \
    -j="-Dbrowserstack.os={OS},-Dbrowserstack.os_version="{OS_Version}",-Dbrowserstack.browser={browser},-Dbrowserstack.browser_version={browser_version},-Dbrowserstack.username={USERNAME},-Dbrowserstack.key={KEY}" \
    -t=@BrowserStack
```
       
5. Visit [browserstack](https://www.browserstack.com/automate) to look at the results. Make sure you choose "Local" Build and "HTS" Project

ZAP (pen testing)
-----------------

You need to have ZAP installed and running locally via command:
```
zap.sh -daemon -config api.disablekey=true -port 11000
```
from inside your zaproxy folder. Download and install ZAP from [here](https://github.com/zaproxy/zaproxy/wiki/Downloads)

Once zap is up and running, run the script:
```
./run_zap_tests_locally.sh
```

Endpoints
=========

Main Public API
---------------
| Path                                                        | Method | Description  |
| ------------------------------------------------------------| ------ | ------------ |
|/help-to-save/                                               |   GET  | Redirects the user to the about-help-to-save page|
|/help-to-save/apply-for-help-to-save/about-help-to-save      |   GET  | Redirects the user to the about-help-to-save page|

Eligibility Check API
---------------------
| Path                                                        | Method | Description  |
| ------------------------------------------------------------| -------| ------------ |
|/help-to-save/check-eligibility                              |  GET   | Endpoint to trigger user eligibility check on DES|
|/help-to-save/not-eligible                                   |  GET   | Displays a page to the user showing they are not eligible|
|/help-to-save/eligible                                       |  GET   | Displays a page to the user showing they are eligible|
|/help-to-save/eligible                                       |  POST  | If eligibility check has been done, the user is directed to the give or select email page|
|/help-to-save/missing-details                                |  GET   | Page showing user which details we are missing and what to do|
|/help-to-save/think-you-are-eligible                         |  GET   | Page showing user what to do if they think they have been incorrectly told they are ineligible for HTS|
|/help-to-save/cannot-check-details                           |  GET   | Page shown to user if we cannot get the users details in the backend systems or they use an unsupported verification mechanism (e.g. Verify)|

Register API
------------
| Path                                                        | Method | Description  |
| ------------------------------------------------------------| ------ | ------------ |
|/help-to-save/apply                                          |  POST  | If the user is logged into GG, their eligibility is checked|
|/help-to-save/try-again-tomorrow                             |  GET   | Displays a page when user cap has been reached (Private Beta Only)|
|/help-to-save/try-again-later-in-year                        |  GET   | Displays a page when total user cap has been reached (Private Beta Only)|
|/help-to-save/select-email                                   |  GET   | Page where user selects existing or new email address to use for help-to-save|
|/help-to-save/select-email                                   |  POST  | Submits an eligible user's selected email address to use for help-to-save|
|/help-to-save/enter-uk-bank-details                          |  GET   | Displays a form for the user to enter in their bank details |
|/help-to-save/enter-uk-bank-details                          |  POST  | Submits bank details to the backend systems|
|/help-to-save/create-account                                 |  GET   | Shows a page to the user where they can check their details and choose to create an account|
|/help-to-save/create-account                                 |  POST  | Submit's users intent to create a HTS account|
|/help-to-save/incorrect-details                              |  GET   | Page showing user what to do if the details shown are incorrect|
|/help-to-save/service-unavailable                            |  GET   | Displays an error page to the user when the service is unavailable|
|/help-to-save/change-bank-details                            |  GET   | Internal endpoint to facilitate changing bank details from the check details page|
|/help-to-save/change-email-address                           |  GET   | Internal endpoint to facilitate changing email addresses from the check details page|


IV API
------
| Path                                                        | Method | Description  |
| ------------------------------------------------------------| -------| ------------ |
|/help-to-save/iv/journey-result                              |   GET  | The continue URL we pass to Auth to return users after IV|
|/help-to-save/identity-verified                              |   GET  | Page showing user their identity has been successfully verified|
|/help-to-save/failed-iv-matching                             |   GET  | Page showing user we were unable to verify their identity|
|/help-to-save/failed-iv                                      |   GET  | Page showing user they didn't answer all iv questions correctly|
|/help-to-save/failed-iv-insufficient-evidence                |   GET  | Page showing user they need to call us to supply more information|
|/help-to-save/failed-iv-locked-out                           |   GET  | Page showing user they have tried to verify their identity too many times so they are now locked out|
|/help-to-save/failed-iv-user-rejected                        |   GET  | Page showing user we were unable to confirm their identity|
|/help-to-save/failed-iv-time-out                             |   GET  | Page showing user their session has ended|
|/help-to-save/failed-iv-technical-issue                      |   GET  | Page showing user we have some technical issue|
|/help-to-save/failed-iv-precondition-failed                  |   GET  | Page showing user they are not able to use this service|


New Applicant Email Address Update API
--------------------------------------
| Path                                                        | Method  | Description  |
| ------------------------------------------------------------| ------- | ------------ |
|/help-to-save/email-verified-callback                        |   GET   | Users are redirected here after they have verified their email|
|/help-to-save/email-verified                                 |   GET   | Page showing the user their email address has been successfully verified|
|/help-to-save/email-updated                                  |   GET   | Page showing user their email address was successfully updated for help-to-save|
|/help-to-save/email-updated                                  |   POST  | User is directed to the final step of the create account journey|
|/help-to-save/enter-email                                    |   GET   | Page for user to enter their email address|
|/help-to-save/enter-email                                    |   POST  | Submits user's given email address to use for help-to-save|
|/help-to-save/confirm-email/:email                           |   GET   | Stores user's confirmed email|
|/help-to-save/verify-email                                   |   GET   | Submits the users new email address to the email verification service|
|/help-to-save/cannot-change-email                            |   GET   | Page showing the user options whether they want to continue when we cannot currently change their email address|
|/help-to-save/cannot-change-email                            |   POST  | Redirects user depending on if they choose to continue or not|
|/help-to-save/cannot-change-email-try-later                  |   GET   | Page showing user that they need to try again later to change their email address|

Access Account API
------------------
| Path                                                        | Method | Description  |
| ------------------------------------------------------------| ------ | ------------ |
|/help-to-save/access-account                                 |   GET  | Redirects the user to their account page on NSI if they have an account|
|/help-to-save/no-account                                     |   GET  | Page showing user that they do not have a help-to-save account|

Account Holder Update Email Address API
---------------------------------------
| Path                                                        | Method | Description  |
| ------------------------------------------------------------| -------| ------------ |
|/help-to-save/account-home/change-email                      |  GET   | Directs an account holder to a page for them to enter a new email address|
|/help-to-save/account-home/change-email                      |  POST  | Submits the account holders new email address to the email verification service|
|/help-to-save/account-home/verify-email                      |  GET   | Page showing user that they need to verify their email address via an email sent to them|
|/help-to-save/account-home/email-verified-callback           |  GET   | This url is given to the email verification service for them to direct back to our service|
|/help-to-save/account-home/email-verified                    |  GET   | Account holders are redirected here after they have verified their email|

Close Account API
---------------- 
| Path                                                        | Method | Description  |
| ------------------------------------------------------------| -------| ------------ |
|/help-to-save/account-home/close-account-are-you-sure        |  GET   | Displays a page informing users how to close their account if they wish to and the consequences of doing so|



IP Whitelisting API
-------------------
| Path                                                        | Method | Description  |
| ------------------------------------------------------------| -------| ------------ |
|/help-to-save/forbidden                                      |   GET  | Page shown if the user is not whitelisted if IP-whitelisting is enabled|

Privacy Statement
-----------------
| Path                                                        | Method | Description  |
| ------------------------------------------------------------| ------ | ------------ |
|/help-to-save/privacy-statement                              |  GET   | Displays a page to the user showing the privacy statement|


License
=======

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
