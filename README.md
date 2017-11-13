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

To request a Help to Save (‘HtS’) account, customers will access a HtS landing page apply.help-to-save.tax.service.gov.uk/about
fronted by Akamai. Once authenticated via Government Gateway, the customer will access the HtS Service whereby the customer may
check their HtS Eligibility check via DES API #2A which in turn collates responses from HoD’s NTC (for WTC), ITMP (for WTC)
and DWP (for UC). The response from DES will confirm whether the customer is eligible to a HtS Account and if so, the customer is
able to continue the journey and create a Help To Save account from a button which in turn invokes an NS&I /createaccount endpoint,
secured by mTLS, whereupon they are redirected to NS&I’s Help To Save web portal frontend.  At this time, in parallel, the HtS service
invokes DES API #4a to “Update HtS Account Status” flag.

One the customer has created an account and lands at NS&I, they will from then on be redirected on login from either party back to the
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
./run_selenium_system_test.sh qa chrome /usr/local/bin/chromedriver wip     # (2) runs selenium scenarios tagged with the `@wip` tag on the
                                                                                #     QA environment using chrome
./run_selenium_system_test.sh dev chrome /usr/local/bin/chromedriver @wip   # (3) the same as (2)
./run_selenium_system_test.sh local chrome /usr/local/bin/chromedriver wip sit # (4) runs selenium scenarios tagged with either the `@wip` or `@sit`
                                                                                #     tags locally using chrome
```

If you wish to run the Selenium tests from Intellij, you`ll need to:
1. Install the Cucumber for Java plugin.
2. In "Edit configurations" > "Cucumber java" > "VM options" enter, for example: -Dbrowser=chrome -Denvironment=dev -Ddrivers=/usr/local/bin
3. In "Edit configurations" > "Cucumber java" > "Glue" enter: hts.steps

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
|`/help-to-save/apply-for-help-to-save`                         |        GET        | Redirects the user to the about-help-to-save page|
|`/help-to-save/apply-for-help-to-save/about-help-to-save`      |        GET        | Displays the about-help-to-page|
|`/help-to-save/apply-for-help-to-save/eligibility`             |        GET        | Displays information about Eligibility for help-to-save|
|`/help-to-save/apply-for-help-to-save/how-the-account-works`   |        GET        | Displays information about how help-to-save works|
|`/help-to-save/apply-for-help-to-save/how-we-calculate-bonuses`|        GET        | Displays information about bonuses
|`/help-to-save/apply-for-help-to-save/apply`                   |        GET        | Displays information about how to apply for help-to-save|

## Register API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/register/create-an-account`                     |        GET        | Directs the user to the help-to-save create an account page|
|`/help-to-save/register/create-an-account`                     |        POST       | Submits an eligible user`s details to create an account|
|`/help-to-save/register/user-cap-reached`                      |        GET        | Displays a page when user cap has been reached (Private Beta Only)|
|`/help-to-save/register/give-email`                            |        GET        | Directs the user to a page where they can supply a new email address|
|`/help-to-save/register/give-email-submit`                     |        POST       | Submits an eligible user`s new email address to the verification service|
|`/help-to-save/select-email`                                   |        GET        | Page where user selects existing or new email address to use for help-to-save|
|`/help-to-save/register/select-email-submit`                   |        POST       | If given, submits a new email to the email verification service. Otherwise continues the create account journey|
|`/help-to-save/details-are-incorrect`                          |        GET        | Page showing user what to do if the details shown are incorrect|

## IV API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/iv/journey-result`                              |        GET        | The continue URL we pass to Auth to return users after IV|

## New Applicant Email Address Update API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/register/verify-email/:email`                   |        GET        | Submits the users new email address to the email verification service|
|`/help-to-save/register/email-verified`                        |        GET        | Users are redirected here after they have verified their email|
|`/help-to-save/register/email-updated`                         |        GET        | Page showing user their email address was successfully updated for help-to-save|


## Eligibility Check API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/check-eligibility`                              |        GET        | Endpoint to trigger user eligibility check on DES|
|`/help-to-save/not-eligible`                                   |        GET        | Displays a page to the user showing they are not eligible|
|`/help-to-save/eligible`                                       |        GET        | Displays a page to the user showing they are eligible|


## Access Account API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/access-account`                                 |        GET        | Redirects the user to their account page on NSI if they have an account|

## Account Holder Update Email Address API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/account/update-your-email-address`              |        GET        | Directs an account holder to a page for them to enter a new email address|
|`/help-to-save/account/verify-email`                           |        POST       | Submits the account holders new email address to the email verification service|
|`/help-to-save/account/email-verified`                         |        GET        | Account holders are redirected here after they have verified their email|
|`/help-to-save/account/email-updated`                          |        GET        | Page showing account holder their email address was successfully updated for help-to-save|
|`/help-to-save/account/email-update-error`                     |        GET        | Page showing account holder their email update request has been unsuccessful|

## IP Whitelisting API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save/forbidden`                                      |        GET        | Page shown if the user is not whitelisted if IP-whitelisting is enabled|


License
---

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").