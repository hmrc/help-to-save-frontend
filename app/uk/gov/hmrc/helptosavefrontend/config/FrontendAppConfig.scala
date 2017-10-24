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

import java.net.URI

import uk.gov.hmrc.helptosavefrontend.models.iv.JourneyId
import uk.gov.hmrc.helptosavefrontend.util._
import uk.gov.hmrc.play.config.ServicesConfig

trait AppConfig {
  val analyticsToken: String
  val analyticsHost: String
  val reportAProblemPartialUrl: String
  val reportAProblemNonJSUrl: String
}

object FrontendAppConfig extends AppConfig with ServicesConfig {

  def getUrlFor(service: String): String = getString(s"microservice.services.$service.url")

  val authUrl: String = baseUrl("auth")

  val helpToSaveUrl: String = baseUrl("help-to-save")

  val checkEligibilityUrl: String = s"${baseUrl("help-to-save-frontend")}/help-to-save/check-eligibility"

  val accessAccountUrl: String = s"${baseUrl("help-to-save-frontend")}/help-to-save/access-account"

  val ivJourneyResultUrl: String = s"${baseUrl("identity-verification")}/mdtp/journey/journeyId"

  val origin: String = getString("appName")

  def encodedCallbackUrl(redirectOnLoginURL: String): String =
    urlEncode(s"${baseUrl("help-to-save-frontend")}/iv/journey-result?continueURL=$redirectOnLoginURL")

  val ivUpliftUrl: String = s"${baseUrl("identity-verification")}${getUrlFor("identity-verification")}"

  def ivUrl(redirectOnLoginURL: String): String = {

    new URI(s"$ivUpliftUrl" +
      s"?origin=$origin" +
      s"&completionURL=${encodedCallbackUrl(redirectOnLoginURL)}" +
      s"&failureURL=${encodedCallbackUrl(redirectOnLoginURL)}" +
      "&confidenceLevel=200"
    ).toString
  }

  def ivJourneyResultUrl(journeyId: JourneyId): String = new URI(s"$ivJourneyResultUrl/${journeyId.Id}").toString

  val verifyEmailURL: String = s"${baseUrl("email-verification")}${getUrlFor("email-verification")}"
  val linkTTLMinutes: Int = getInt("microservice.services.email-verification.linkTTLMinutes")
  val helpToSaveFrontendUrl: String = baseUrl("help-to-save-frontend")
  val newApplicantContinueURL: String = s"$helpToSaveFrontendUrl/help-to-save/register/email-verified"
  val accountHolderContinueURL: String = s"$helpToSaveFrontendUrl/help-to-save/account/email-verified"

  val nsiAuthHeaderKey: String = getString("microservice.services.nsi.client.httpheader.header-key")

  val nsiBasicAuth: String = {
    val user = new String(base64Decode(getString("microservice.services.nsi.client.httpheader.basicauth.Base64User")))
    val password = new String(base64Decode(getString("microservice.services.nsi.client.httpheader.basicauth.Base64Password")))
    val encoding = getString("microservice.services.nsi.client.httpheader.encoding")

    s"Basic: ${new String(base64Encode(s"$user:$password"), encoding)}"
  }

  val nsiCreateAccountUrl: String = s"${baseUrl("nsi")}${getString("microservice.services.nsi.create-account-url")}"

  val nsiUpdateEmailUrl: String = s"${baseUrl("nsi")}${getString("microservice.services.nsi.update-email-url")}"

  val nsiHealthCheckUrl: String = s"${baseUrl("nsi")}${getString("microservice.services.nsi.health-check-url")}"

  val sessionCacheKey: String = getString("microservice.services.keystore.session-key")

  val keyStoreUrl: String = baseUrl("keystore")

  val keyStoreDomain: String = getString("microservice.services.keystore.domain")

  val personalTaxAccountUrl: String = s"${baseUrl("pertax-frontend")}${getUrlFor("pertax-frontend")}"

  val feedbackSurveyUrl: String = s"${baseUrl("feedback-survey")}${getUrlFor("feedback-survey")}"

  val caFrontendUrl: String = s"${baseUrl("company-auth-frontend")}${getUrlFor("company-auth-frontend")}"

  val ggLoginUrl: String = s"$caFrontendUrl/sign-in"

  val signOutUrl: String = s"$caFrontendUrl/sign-out?continue=$feedbackSurveyUrl?origin=HTS"

  override lazy val analyticsToken: String = getString("google-analytics.token")
  override lazy val analyticsHost: String = getString("google-analytics.host")

  val contactUrl: String = s"${baseUrl("contact-frontend")}${getUrlFor("contact-frontend")}"
  override lazy val reportAProblemPartialUrl: String = s"$contactUrl/contact/problem_reports_ajax?service=$origin"
  override lazy val reportAProblemNonJSUrl: String = s"$contactUrl/contact/problem_reports_nonjs?service=$origin"

}
