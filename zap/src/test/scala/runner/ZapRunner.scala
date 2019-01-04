/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package runner

import uk.gov.hmrc.ZapTest

class ZapRunner extends ZapTest {

  /**
   * zapBaseUrl is a required field - you'll need to set it in this file, for your project to compile.
   * It will rarely need to be changed. We've included it as an overridable field
   * for flexibility and just in case. The ZapTestBuilder (which creates the jenkins job
   *  you will use for this trial) starts ZAP on port 11000. For ease you might want to use the same
   *  port while testing locally.
   */
  override val zapBaseUrl: String = "http://localhost:11000"

  /**
   * testUrl is a required field - you'll need to set it in this file, for your project to compile.
   * It needs to be the URL of the start page of your application (not just localhost:port).
   */
  override val testUrl: String = "http://localhost:7000/help-to-save/apply-for-help-to-save/about-help-to-save"

  /**
   * alertsBaseUrl is not a required field. This is the url that the zap-automation library
   * will use to filter out the alerts that are shown to you. Note that while Zap is doing
   * testing, it is likely to find alerts from other services that you don't own - for example
   * from logging in, therefore we recommend that you set this to be the base url for the
   * service you are interested in.
   */
  override val alertsBaseUrl: String = "http://localhost:7000"

  /**
   * contextBaseUrl is not a required field. This url is added as the base url to your
   * context.
   * A context is a construct in Zap that limits the scope of any attacks run to a
   * particular domain (this doesn't mean that Zap won't find alerts on other services during the
   * browser test run).
   * This would usually be the base url of your service - eg http://localhost:9949.*
   */
  //override val contextBaseUrl: String = "http://localhost:7000.*"

  /**
   * desiredTechnologyNames is not a required field. We recommend you don't change this
   * field, as we've made basic choices for the platform. We made it overridable just in case
   * your service differs from the standards of the Platform.
   *
   * The technologies that you put here will limit the amount of checks that ZAP will do to
   * just the technologies that are relevant. The default technologies are set to
   * "OS,OS.Linux,Language,Language.Xml,SCM,SCM.Git" - which is all you will need to test a
   * service on MDTP.
   */
  //override val desiredTechnologyNames: String = ""

  /**
   * routesToBeIgnoredFromContext is not a required field. You may set this if you have any routes
   * that are part of your application, but you do not want tested. For example, if you had any
   * test-only routes, you could force Zap not to test them by adding them in here as a regex.
   */
  //override val routeToBeIgnoredFromContext: String = "http://localhost:9032/assets/*"

  /**
   * If, when you run the Zap tests, you find alerts that you have investigated and don't see as a problem
   * you can filter them out using this code, on the cweid and the url that the alert was found on.
   * The CWE ID is a Common Weakness Enumeration (http://cwe.mitre.org/data/index.html), you can
   * find this by looking at the alert output from your tests.
   */

}
