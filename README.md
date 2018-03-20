[![Build Status](https://travis-ci.org/hmrc/help-to-save-frontend.svg)](https://travis-ci.org/hmrc/help-to-save-frontend) [ ![Download](https://api.bintray.com/packages/hmrc/releases/help-to-save-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/help-to-save-frontend/_latestVersion)

## Help to Save Frontend 

Frontend application process for Help to Save.
 
## Product Repos

The suite of repos connected with this Product are as follows:  

https://github.com/hmrc/help-to-save-frontend <br />
https://github.com/hmrc/help-to-save <br />
https://github.com/hmrc/help-to-save-frontend-stub <br />
..plus this repo is the JSON create account interface schema between HMRC and NS&I: <br />
https://github.com/hmrc/help-to-save-apis/blob/master/1.0/create-account.request.schema.json

## Keywords

| Key | Meaning |
|:----------------:|-------------|
|DES| Data Exchange Service (Message Bus) |
|HoD| Head Of Duty, HMRC legacy application |
|HtS| Help To Save |
|MDTP| HMRC Multi-channel Digital Tax Platform |
|NS&I| National Savings & Investments |
|UC| Universal Credit|
|WTC| Working Tax Credit|

## Product Background

The Prime Minister set out the government’s intention to bring forward, a new Help to Save
(‘HtS’) scheme to encourage people on low incomes to build up a “rainy day” fund.

Help to Save will target working families on the lowest incomes to help them build up their
savings. The scheme will be open to 3.5 million adults in receipt of Universal Credit with
minimum weekly household earnings equivalent to 16 hours at the National Living Wage, or those in receipt of Working Tax Credit.

A customer can deposit up to a maximum of £50 per month in the account. It will work by
providing a 50% government bonus on the highest amount saved into a HtS account. The
bonus is paid after two years with an option to save for a further two years, meaning that people
can save up to £2,400 and benefit from government bonuses worth up to £1,200. Savers will be
able to use the funds in any way they wish. The published implementation date for this is Q2/2018,
but the project will have a controlled go-live with a pilot population in Q1/2018.

## Context of this microservice

To request a Help to Save (‘HtS’) account, customers will access a HtS landing page /help-to-save/about-help-to-save
fronted by Akamai. Once authenticated via Government Gateway, the customer will access the HtS Service whereby the customer may
check their HtS Eligibility check via DES API #2A which in turn collates responses from HoD’s NTC (for WTC), ITMP (for WTC)
and DWP (for UC). The response from DES will confirm whether the customer is eligible to a HtS Account and if so, the customer is
able to continue the journey and create a Help To Save account from a button which in turn invokes an NS&I /createaccount endpoint,
secured by mTLS, whereupon they are redirected to NS&I’s Help To Save web portal frontend.  At this time, in parallel, the HtS service
invokes DES API #4a to “Update HtS Account Status” flag.

Once the customer has created an account and lands at NS&I, they will from then on be redirected on login from either party back to the
NS&I landing page /homepage.  At this point, NS&I may redirect to other internal NS&I pages depending on the customer’s status.

When at NS&I, the customer will only be redirected back to the MDTP frontend microservice:
if the HMRC/MDTP Auth session expires after 4hrs.
when they click the “Change Email” link which redirects to the service Change Email endpoint.
when they click the “Feedback” link which redirects to HMRC Deskpro HtS team.
when they click the “Sign Out” link which redirects to an anonymous HMRC Exit Survey.

## Private Beta User Restriction

During Private Beta, when a HtS Account is created, per-day-count and total-count counters are incremented. After the customer’s Eligibility
Check, the counters are checked to ensure that the cap’s haven’t been reached. If they have, they are shuttered, otherwise they may continue
to create a HtS account.

Requirements
------------

This service is written in [Scala](http://www.scala-lang.org/) and [Play](http://playframework.com/), so needs at least a [JRE] to run.

## How to run

Runs on port 7000 when started locally by the service manager.

Start service manager with the following command to run the service with all required dependencies:

```
sm --start HTS_ALL -f
```

## How to test
Selenium system tests are distinguished from unit tests by having `SeleniumSystemTest` in the relevant runner name. Note
that you will need to download Selenium drivers from http://docs.seleniumhq.org/download/. The exact version of a driver
to be downloaded will depend on the version of the corresponding browser - the versions of the driver and browser must be
compatible. Mac users will have to rename the downloaded `chromedriver` file to `chromedriver_mac`.

The unit tests can be run by running
```
sbt test
```
This command will not run the Selenium tests.

Only if you want to run the Selenium tests locally, execute the following commands to start the relevant services:

sm --start HTS_DEP -f

cd {front end path}

sbt "run 7000"

cd {back end path}

sbt "run 7001"

cd {stub path}

sbt "run 7002"

Then (to run against any environment) execute:
 ```
 ./run_selenium_system_test.sh -e=${ENV} -b=${BROWSER} -d=${DRIVERS} -r=${rootUrl}
```
where `${ENV}` indicates the environment the tests should run on (one of `dev`, `qa` or `local`), `${BROWSER}` is
the browser the tests should run on `chrome` and `${DRIVERS}` is the path to the folder
containing the Selenium driver files. This command will not run the unit tests. To run only a subset of
Selenium scenarios, tag the relevant scenarios and then run the command
 ```
 ./run_selenium_system_test.sh -e=${ENV} -b=${BROWSER} -d=${DRIVERS} -r=${rootUrl} -t=${TAGS}
 ```
where `${TAGS}` is a comma separated list containing the relevant tags. Examples:

```
./run_selenium_system_test.sh -e=dev -b=chrome -d=/usr/local/bin/chromedriver -r={mdtp dev host url}                # (1) runs all selenium tests on the dev environment using chrome
./run_selenium_system_test.sh -e=qa -b=chrome -d=/usr/local/bin/chromedriver  -r={mdtp qa host url}  -t="wip"        # (2) runs selenium scenarios tagged with the `@wip` tag on the
                                                                                             #     QA environment using chrome
./run_selenium_system_test.sh -e=dev -b=chrome -d=/usr/local/bin/chromedriver -r={mdtp dev host url}  -t="wip"       # (3) the same as (2)
./run_selenium_system_test.sh -e=local -b=chrome -d=/usr/local/bin/chromedriver -t="wip,sit" # (4) runs selenium scenarios tagged with either the `@wip` or `@sit`
                                                                                             #     tags locally using chrome
```

If you wish to run the Selenium tests from Intellij, you`ll need to:
1. Install the Cucumber for Java plugin.
2. In "Edit configurations" > "Cucumber java" > "VM options" enter, for example: -Dbrowser=chrome -Denvironment=dev -Ddrivers=/usr/local/bin
3. In "Edit configurations" > "Cucumber java" > "Glue" enter: hts.steps

## BrowserStack (cross-browser/-platform compatibility testing)

NOTE: BrowserStack has compatibility issues with Safari accessing localhost, so please run tests against Safari MANUALLY at https://www.browserstack.com/start. All iOS devices run Safari by default.

To run parallel tests using BrowserStack in AUTOMATED mode, you need to:
0. If necessary, start mongod using "sudo service mongod start"
1. Start service manager using HTS_ALL
2. Run the BrowserStackLocal file in this project up to four times using:
./BrowserStackLocal --key {KEY} --local-identifier {1/2/3/4} (the script is configured to accept either no local identifier or just 1/2/3/4)
3. Once you have the desired number of BrowserStack instances running locally,
configure the following script to use the desired OS/Browser combination by
replacing the placeholders with the values listed at https://www.browserstack.com/automate/capabilities or
you can visit the spreadsheet at https://docs.google.com/spreadsheets/d/1xa9h-A7goX3yOd954Oo2sdE2KNUsAWpWa6VFCXHKt-g/edit#gid=0 for example commands:
./run_selenium_system_test.sh -e=local -b=browserstack{1/2/3/4} -d=BrowserStackLocal -j="-Dbrowserstack.os={OS},-Dbrowserstack.os_version="{OS_Version}",-Dbrowserstack.device={device},-Dbrowserstack.real_mobile=true,-Dbrowserstack.username={USERNAME},-Dbrowserstack.key={KEY}" -t=@BrowserStack
./run_selenium_system_test.sh -e=local -b=browserstack{1/2/3/4} -d=BrowserStackLocal -j="-Dbrowserstack.os={OS},-Dbrowserstack.os_version="{OS_Version}",-Dbrowserstack.browser={browser},-Dbrowserstack.browser_version={browser_version},-Dbrowserstack.username={USERNAME},-Dbrowserstack.key={KEY}" -t=@BrowserStack
4. Visit https://www.browserstack.com/automate to look at the results. Make sure you choose "Local" Build and "HTS" Project

## ZAP (pen testing)

You need to have ZAP installed and running locally via command:
zap.sh -daemon -config api.disableKey=true -port 11000
from inside your zaproxy folder
download and install ZAP from here:
https://github.com/zaproxy/zaproxy/wiki/Downloads

## How to deploy

This microservice is deployed as per all MDTP microservices via Jenkins into a Docker slug running on a Cloud Provider.

## Endpoints

## Main Public API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/`                                               |        GET        | Redirects the user to the about-help-to-save page|
|`/help-to-save/apply-for-help-to-save/about-help-to-save`      |        GET        | Displays the about-help-to-page|
|`/help-to-save/about-help-to-save`                             |        GET        | Displays the about-help-to-page|
|`/help-to-save/eligibility`                                    |        GET        | Displays information about Eligibility for help-to-save|
|`/help-to-save/how-the-account-works`                          |        GET        | Displays information about how help-to-save works|
|`/help-to-save/how-we-calculate-bonuses`                       |        GET        | Displays information about bonuses|
|`/help-to-save/apply`                                          |        GET        | Displays information about how to apply for help-to-save|

## Register API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/apply`                                          |        POST       | If the user is logged into GG, their eligibility is checked|
|`/help-to-save/create-account`                                 |        GET        | Directs the user to the help-to-save create an account page|
|`/help-to-save/create-account`                                 |        POST       | Submits an eligible user`s details to create an account|
|`/help-to-save/try-again-tomorrow`                             |        GET        | Displays a page when user cap has been reached (Private Beta Only)|
|`/help-to-save/try-again-later-in-year`                        |        GET        | Displays a page when total user cap has been reached (Private Beta Only)|
|`/help-to-save/select-email`                                   |        GET        | Page where user selects existing or new email address to use for help-to-save|
|`/help-to-save/select-email`                                   |        POST       | Submits an eligible user's selected email address to use for help-to-save|
|`/help-to-save/incorrect-details`                              |        GET        | Page showing user what to do if the details shown are incorrect|
|`/help-to-save/service-unavailable`                            |        GET        | Displays a page to the user when the service is unavailable|

## IV API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/iv/journey-result`                              |        GET        | The continue URL we pass to Auth to return users after IV|
|`/help-to-save/identity-verified`                              |        GET        | Page showing user their identity has been successfully verified|
|`/help-to-save/failed-iv-matching`                             |        GET        | Page showing user we were unable to verify their identity|
|`/help-to-save/failed-iv`                                      |        GET        | Page showing user they didn't answer all iv questions correctly|
|`/help-to-save/failed-iv-insufficient-evidence`                |        GET        | Page showing user they need to call us to supply more information|
|`/help-to-save/failed-iv-locked-out`                           |        GET        | Page showing user they have tried to verify their identity too many times so they are now locked out|
|`/help-to-save/failed-iv-user-rejected`                        |        GET        | Page showing user we were unable to confirm their identity|
|`/help-to-save/failed-iv-time-out`                             |        GET        | Page showing user their session has ended|
|`/help-to-save/failed-iv-technical-issue`                      |        GET        | Page showing user we have some technical issue|
|`/help-to-save/failed-iv-precondition-failed`                  |        GET        | Page showing user they are not able to use this service|

## New Applicant Email Address Update API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/email-verified-callback`                        |        GET        | Users are redirected here after they have verified their email|
|`/help-to-save/email-verified`                                 |        GET        | Page showing the user their email address has been successfully verified|
|`/help-to-save/email-updated`                                  |        GET        | Page showing user their email address was successfully updated for help-to-save|
|`/help-to-save/email-updated`                                  |        POST       | User is directed to the final step of the create account journey|
|`/help-to-save/enter-email`                                    |        GET        | Page for user to enter their email address|
|`/help-to-save/enter-email`                                    |        POST       | Submits user's given email address to use for help-to-save|
|`/help-to-save/confirm-email/:email`                           |        GET        | Stores user's confirmed email|
|`/help-to-save/verify-email`                                   |        GET        | Submits the users new email address to the email verification service|
|`/help-to-save/cannot-change-email`                            |        GET        | Page showing the user options whether they want to continue when we cannot currently change their email address|
|`/help-to-save/cannot-change-email`                            |        POST       | Redirects user depending on if they choose to continue or not|
|`/help-to-save/cannot-change-email-try-later`                  |        GET        | Page showing user that they need to try again later to change their email address|

## Eligibility Check API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/check-eligibility`                              |        GET        | Endpoint to trigger user eligibility check on DES|
|`/help-to-save/not-eligible`                                   |        GET        | Displays a page to the user showing they are not eligible|
|`/help-to-save/eligible`                                       |        GET        | Displays a page to the user showing they are eligible|
|`/help-to-save/eligible`                                       |        POST       | If eligibility check has been done, the user is directed to the give or select email page|
|`/help-to-save/missing-details`                                |        GET        | Page showing user which details we are missing and what to do|

## Access Account API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/access-account`                                 |        GET        | Redirects the user to their account page on NSI if they have an account|
|`/help-to-save/no-account`                                     |        GET        | Page showing user that they do not have a help-to-save account|

## Account Holder Update Email Address API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/account-home/change-email`                      |        GET        | Directs an account holder to a page for them to enter a new email address|
|`/help-to-save/account-home/change-email`                      |        POST       | Submits the account holders new email address to the email verification service|
|`/help-to-save/account-home/verify-email`                      |        GET        | Page showing user that they need to verify their email address via an email sent to them|
|`/help-to-save/account-home/email-verified-callback`           |        GET        | This url is given to the email verification service for them to direct back to our service|
|`/help-to-save/account-home/email-verified`                    |        GET        | Account holders are redirected here after they have verified their email|

## IP Whitelisting API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/forbidden`                                      |        GET        | Page shown if the user is not whitelisted if IP-whitelisting is enabled|

## Privacy Statement

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/privacy-statement`                              |        GET        | Displays a page to the user showing the privacy statement|

License
---

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

