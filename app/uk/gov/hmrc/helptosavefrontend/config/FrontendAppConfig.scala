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

  val appName: String = getString("appName")

  private def getUrlFor(service: String) = getString(s"microservice.services.$service.url")

  val authUrl: String = baseUrl("auth")

  val helpToSaveUrl: String = baseUrl("help-to-save")

  val helpToSaveFrontendUrl: String = getUrlFor("help-to-save-frontend")

  val checkEligibilityUrl: String = s"${getUrlFor("help-to-save-frontend")}/check-eligibility"

  val accessAccountUrl: String = s"${getUrlFor("help-to-save-frontend")}/access-account"

  val ivJourneyResultUrl: String = s"${getUrlFor("identity-verification-journey-result")}/journey/journeyId"

  def encodedCallbackUrl(redirectOnLoginURL: String): String =
    urlEncode(s"${getUrlFor("help-to-save-frontend")}/iv/journey-result?continueURL=$redirectOnLoginURL")

  val ivUpliftUrl: String = s"${getUrlFor("identity-verification")}/uplift"

  def ivUrl(redirectOnLoginURL: String): String = {

    new URI(s"$ivUpliftUrl" +
      s"?origin=$appName" +
      s"&completionURL=${encodedCallbackUrl(redirectOnLoginURL)}" +
      s"&failureURL=${encodedCallbackUrl(redirectOnLoginURL)}" +
      "&confidenceLevel=200"
    ).toString
  }

  def ivJourneyResultUrl(journeyId: JourneyId): String = new URI(s"$ivJourneyResultUrl/${journeyId.Id}").toString

  val verifyEmailURL: String = s"${baseUrl("email-verification")}/email-verification/verification-requests"

  val linkTTLMinutes: Int = getInt("microservice.services.email-verification.linkTTLMinutes")

  val newApplicantContinueURL: String = s"$helpToSaveFrontendUrl/register/email-verified"

  val accountHolderContinueURL: String = s"$helpToSaveFrontendUrl/account/email-verified"

  val nsiAuthHeaderKey: String = getString("microservice.services.nsi.client.httpheader.header-key")

  val nsiBasicAuth: String = {
    val user = new String(base64Decode(getString("microservice.services.nsi.client.httpheader.basicauth.Base64User")))
    val password = new String(base64Decode(getString("microservice.services.nsi.client.httpheader.basicauth.Base64Password")))
    val encoding = getString("microservice.services.nsi.client.httpheader.encoding")

    s"Basic: ${new String(base64Encode(s"$user:$password"), encoding)}"
  }

  val nsiCreateAccountUrl: String = s"${baseUrl("nsi")}/nsihts/createaccount"

  val sessionCacheKey: String = getString("microservice.services.keystore.session-key")

  val keyStoreUrl: String = baseUrl("keystore")

  val keyStoreDomain: String = getString("microservice.services.keystore.domain")

  val personalTaxAccountUrl: String = s"${getUrlFor("pertax-frontend")}"

  val feedbackSurveyUrl: String = s"${getUrlFor("feedback-survey")}"

  val caFrontendUrl: String = s"${getUrlFor("company-auth-frontend")}"

  val ggLoginUrl: String = s"$caFrontendUrl/sign-in"

  val signOutUrl: String = s"$caFrontendUrl/sign-out?continue=$feedbackSurveyUrl?origin=HTS"

  override lazy val analyticsToken: String = getString("google-analytics.token")
  override lazy val analyticsHost: String = getString("google-analytics.host")

  val contactUrl: String = s"${getUrlFor("contact-frontend")}"
  override lazy val reportAProblemPartialUrl: String = s"$contactUrl/contact/problem_reports_ajax?service=$appName"
  override lazy val reportAProblemNonJSUrl: String = s"$contactUrl/contact/problem_reports_nonjs?service=$appName"

}
