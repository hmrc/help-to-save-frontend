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
import java.util.Base64

import uk.gov.hmrc.play.config.ServicesConfig

trait AppConfig {
  val analyticsToken: String
  val analyticsHost: String
  val reportAProblemPartialUrl: String
  val reportAProblemNonJSUrl: String
}

object FrontendAppConfig extends AppConfig with ServicesConfig {

  val helpToSaveUrl: String = baseUrl("help-to-save")

  val CheckEligibilityUrl: String = getConfString("help-to-save-check-eligibility.url", "")

  def confirmYourDetailsUrl(p: String): String = getConfString("help-to-save-email-verification.url", "") + "?p=" + p

  val createAccountUrl: String = s"$helpToSaveUrl/help-to-save/create-an-account"

  val ivUrl = s"${baseUrl("identity-verification-frontend")}/mdtp/journey/journeyId"

  val itmpEnrolmentURL: String = baseUrl("itmp-enrolment")

  def encoded(url: String): String = URLEncoder.encode(url, "UTF-8")

  val ivUpliftUrl: String = getConfString("identity-verification-uplift.url", "")

  val sosOrigin: String = getConfString("appName", "help-to-save-frontend")

  val IdentityCallbackUrl: String = getConfString("identity-callback.url", "")

  val IvRetryUrl: String =
    new URI(s"$ivUpliftUrl?origin=$sosOrigin&" +
      s"completionURL=${URLEncoder.encode(IdentityCallbackUrl, "UTF-8")}&" +
      s"failureURL=${URLEncoder.encode(IdentityCallbackUrl, "UTF-8")}" +
      s"&confidenceLevel=200")
      .toString

  val nsiAuthHeaderKey: String = getString("microservice.services.nsi.authorization.header-key")

  val nsiBasicAuth: String = {
    val user = getString("microservice.services.nsi.authorization.user")
    val password = getString("microservice.services.nsi.authorization.password")
    val encoding = getString("microservice.services.nsi.authorization.encoding")

    val encoded = Base64.getEncoder.encode(s"$user:$password".getBytes)
    s"Basic: ${new String(encoded, encoding)}"
  }

  val nsiUrl = s"${baseUrl("nsi")}${getString("microservice.services.nsi.url")}"

  val sessionCacheKey: String = getString("microservice.services.keystore.session-key")

  val keyStoreUrl: String = baseUrl("keystore")

  val keyStoreDomain: String = getString("microservice.services.keystore.domain")

  val personalAccountUrl: String = getString("microservice.services.pertax-frontend.url")

  val feedbackSurveyUrl: String = getString("microservice.services.feedback-survey.url")

  val caFrontendUrl: String = getString("microservice.services.ca-frontend.url")

  val ggSignOutUrl: String = s"$caFrontendUrl/sign-out"

  val signOutUrl = s"$ggSignOutUrl?continue=$feedbackSurveyUrl?origin=HTS"

  val mongoEncSeed = getString("microservice.mongo-encryption-seed")

  override lazy val analyticsToken: String = getString("google-analytics.token")
  override lazy val analyticsHost: String = getString("google-analytics.host")

  val contactHost: String = getString(s"contact-frontend.host")
  val contactFormServiceIdentifier: String = "MyService"

  override lazy val reportAProblemPartialUrl: String = s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override lazy val reportAProblemNonJSUrl: String = s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"

}
