/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.config

import java.net.{URI, URLEncoder}

import play.api.Play.{configuration, current}
import uk.gov.hmrc.play.config.ServicesConfig

trait AppConfig {
  val analyticsToken: String
  val analyticsHost: String
  val reportAProblemPartialUrl: String
  val reportAProblemNonJSUrl: String
}

object FrontendAppConfig extends AppConfig with ServicesConfig {

  private def loadConfig(key: String) = configuration.getString(key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  private val contactHost = configuration.getString(s"contact-frontend.host").getOrElse("")
  private val contactFormServiceIdentifier = "MyService"

  val HtsDeclarationUrl = getConfString("help-to-save-declaration.url", "")

  private val companyAuthFrontend = getConfString("company-auth.url", throw new RuntimeException("Company auth url required"))
  private val companyAuthSignInPath = getConfString("company-auth.sign-in-path", "")
  val companySignInUrl: String = companyAuthFrontend + companyAuthSignInPath

  val twoFactorUrl: String = getConfString("two-factor.url", "")
  val ivUpliftUrl: String = getConfString("identity-verification-uplift.url", "")
  val verifySignIn = getConfString("verify-sign-in.url", "")
  val sosOrigin: String = getConfString("appName", "help-to-save-frontend")

  val TwoFactorFailedUrl = getConfString("two-factor-failed.url", "")

  val IdentityVerificationURL = getConfString("identity-verification-frontend.url", "")

  val IdentityCallbackUrl = getConfString("identity-callback.url", "")

  val IvRetryUrl: String =
    new URI(s"$ivUpliftUrl?origin=$sosOrigin&" +
      s"completionURL=${URLEncoder.encode(IdentityCallbackUrl, "UTF-8")}&" +
      s"failureURL=${URLEncoder.encode(IdentityCallbackUrl, "UTF-8")}" +
      s"&confidenceLevel=200")
      .toString

  val TwoFactorUrl: String =
    new URI(s"$twoFactorUrl?" +
      s"continue=${URLEncoder.encode(HtsDeclarationUrl, "UTF-8")}&" +
      s"failure=${URLEncoder.encode(TwoFactorFailedUrl, "UTF-8")}")
      .toString

  override lazy val analyticsToken = loadConfig(s"google-analytics.token")
  override lazy val analyticsHost = loadConfig(s"google-analytics.host")
  override lazy val reportAProblemPartialUrl = s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override lazy val reportAProblemNonJSUrl = s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"
}
