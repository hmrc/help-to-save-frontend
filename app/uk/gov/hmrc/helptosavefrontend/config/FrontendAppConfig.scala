/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import uk.gov.hmrc.helptosavefrontend.models.iv.JourneyId
import uk.gov.hmrc.helptosavefrontend.util.urlEncode
import uk.gov.hmrc.play.config.ServicesConfig

@Singleton
class FrontendAppConfig @Inject() (override val runModeConfiguration: Configuration, val environment: Environment) extends ServicesConfig {

  override protected def mode: Mode = environment.mode

  val appName: String = getString("appName")

  private def getUrlFor(service: String) = getString(s"microservice.services.$service.url")

  val authUrl: String = baseUrl("auth")

  val helpToSaveFrontendUrl: String = getUrlFor("help-to-save-frontend")

  val checkEligibilityUrl: String = s"${getUrlFor("help-to-save-frontend")}/check-eligibility"

  val accessAccountUrl: String = s"${getUrlFor("help-to-save-frontend")}/access-account"

  val ivJourneyResultUrl: String = s"${baseUrl("identity-verification-journey-result")}/mdtp/journey/journeyId"

  def encodedCallbackUrl(redirectOnLoginURL: String): String =
    urlEncode(s"$helpToSaveFrontendUrl/iv/journey-result?continueURL=$redirectOnLoginURL")

  val ivUpliftUrl: String = s"${getUrlFor("identity-verification-uplift")}/uplift"

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

  val newApplicantContinueURL: String = s"$helpToSaveFrontendUrl/email-verified-callback"

  val accountHolderContinueURL: String = s"$helpToSaveFrontendUrl/account-home/email-verified-callback"

  val nsiManageAccountUrl: String = getUrlFor("nsi.manage-account")

  val sessionCacheKey: String = getString("microservice.services.keystore.session-key")

  val keyStoreUrl: String = baseUrl("keystore")

  val keyStoreDomain: String = getString("microservice.services.keystore.domain")

  val feedbackSurveyUrl: String = s"${getUrlFor("feedback-survey")}"

  val caFrontendUrl: String = s"${getUrlFor("company-auth-frontend")}"

  val ggLoginUrl: String = s"$caFrontendUrl/sign-in"

  val signOutUrl: String = s"$caFrontendUrl/sign-out?continue=$feedbackSurveyUrl?origin=HTS"

  lazy val analyticsToken: String = getString("google-analytics.token")
  lazy val analyticsHost: String = getString("google-analytics.host")

  val contactFormServiceIdentifier: String = "HTS"

  val contactBaseUrl: String = getUrlFor("contact-frontend")
  lazy val reportAProblemPartialUrl: String = s"$contactBaseUrl/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  lazy val reportAProblemNonJSUrl: String = s"$contactBaseUrl/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"
  lazy val betaFeedbackUrlNoAuth: String = s"$contactBaseUrl/contact/beta-feedback-unauthenticated?service=$contactFormServiceIdentifier"
}
