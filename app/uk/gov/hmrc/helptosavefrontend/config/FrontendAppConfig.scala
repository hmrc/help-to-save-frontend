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

import java.net.{URI, URLDecoder, URLEncoder}
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

  val checkEligibilityUrl: String = getString("microservice.services.help-to-save-check-eligibility.url")

  val accessAccountUrl: String = getString("microservice.services.help-to-save-access-account.url")

  def confirmYourDetailsUrl(p: String): String = getConfString("help-to-save-email-verification.url", "") + "?p=" + p

  val createAccountUrl: String = s"$helpToSaveUrl/help-to-save/create-an-account"

  val ivUrl: String = s"${baseUrl("identity-verification-frontend")}/mdtp/journey/journeyId"

  def encoded(url: String): String = URLEncoder.encode(url, "UTF-8")

  def decoded(url: String): String = URLDecoder.decode(url, "UTF-8")

  val ivUpliftUrl: String = getConfString("microservice.services.identity-verification-uplift.url", "") //TODO: this doesnt work ATM

  val sosOrigin: String = getString("appName")

  val identityCallbackUrl: String = getString("microservice.services.identity-callback.url")

  val IvRetryUrl: String =
    new URI(s"$ivUpliftUrl?origin=$sosOrigin&" +
      s"completionURL=${URLEncoder.encode(identityCallbackUrl, "UTF-8")}&" +
      s"failureURL=${URLEncoder.encode(identityCallbackUrl, "UTF-8")}" +
      "&confidenceLevel=200")
      .toString

  val nsiAuthHeaderKey: String = getString("microservice.services.nsi.authorization.header-key")

  val nsiBasicAuth: String = {
    val user = getString("microservice.services.nsi.authorization.user")
    val password = getString("microservice.services.nsi.authorization.password")
    val encoding = getString("microservice.services.nsi.authorization.encoding")

    val encoded = Base64.getEncoder.encode(s"$user:$password".getBytes)
    s"Basic: ${new String(encoded, encoding)}"
  }

  val nsiCreateAccountUrl: String = s"${baseUrl("nsi")}${getString("microservice.services.nsi.create-account-url")}"

  val nsiUpdateEmailUrl: String = s"${baseUrl("nsi")}${getString("microservice.services.nsi.update-email-url")}"

  val sessionCacheKey: String = getString("microservice.services.keystore.session-key")

  val keyStoreUrl: String = baseUrl("keystore")

  val keyStoreDomain: String = getString("microservice.services.keystore.domain")

  val personalAccountUrl: String = getString("microservice.services.pertax-frontend.url")

  val feedbackSurveyUrl: String = getString("microservice.services.feedback-survey.url")

  val caFrontendUrl: String = getString("microservice.services.ca-frontend.url")

  val ggSignOutUrl: String = s"$caFrontendUrl/sign-out"

  val signOutUrl: String = s"$ggSignOutUrl?continue=$feedbackSurveyUrl?origin=HTS"

  override lazy val analyticsToken: String = getString("google-analytics.token")
  override lazy val analyticsHost: String = getString("google-analytics.host")

  val contactHost: String = getString("contact-frontend.host")
  val contactFormServiceIdentifier: String = "MyService"

  override lazy val reportAProblemPartialUrl: String = s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override lazy val reportAProblemNonJSUrl: String = s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"

}
