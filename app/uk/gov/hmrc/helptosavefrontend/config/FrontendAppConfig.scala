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

package uk.gov.hmrc.helptosavefrontend.config

import java.net.URI

import javax.inject.{Inject, Singleton}
import play.api.Mode.Mode
import play.api.i18n.Lang
import play.api.{Configuration, Environment}
import uk.gov.hmrc.helptosavefrontend.models.iv.JourneyId
import uk.gov.hmrc.helptosavefrontend.util.urlEncode
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.duration.Duration

@Singleton
class FrontendAppConfig @Inject() (override val runModeConfiguration: Configuration, val environment: Environment) extends ServicesConfig {

  override protected def mode: Mode = environment.mode

  val appName: String = getString("appName")

  val version: String = getString("microservice.services.nsi.version")

  val systemId: String = getString("microservice.services.nsi.systemId")

  private def getUrlFor(service: String) = getString(s"microservice.services.$service.url")

  val authUrl: String = baseUrl("auth")

  val helpToSaveFrontendUrl: String = getUrlFor("help-to-save-frontend")

  val checkEligibilityUrl: String = s"${getUrlFor("help-to-save-frontend")}/check-eligibility"

  val accessAccountUrl: String = s"${getUrlFor("help-to-save-frontend")}/access-account"

  val ivJourneyResultUrl: String = s"${baseUrl("identity-verification-journey-result")}/mdtp/journey/journeyId"

  val ivUpliftUrl: String = s"${getUrlFor("identity-verification-uplift")}/uplift"

  val ivFailureUrl: String = getString("gov-uk.url.contact-us")

  def ivUrl(redirectOnLoginURL: String): String = {
      def encodedCallbackUrl(redirectOnLoginURL: String): String =
        urlEncode(s"$helpToSaveFrontendUrl/iv/journey-result?continueURL=$redirectOnLoginURL")

    new URI(s"$ivUpliftUrl" +
      s"?origin=$appName" +
      s"&completionURL=${encodedCallbackUrl(redirectOnLoginURL)}" +
      s"&failureURL=${encodedCallbackUrl(ivFailureUrl)}" +
      "&confidenceLevel=200"
    ).toString
  }

  val caFrontendUrl: String = s"${getUrlFor("company-auth-frontend")}"

  val ggLoginUrl: String = s"$caFrontendUrl/sign-in"
  val ggContinueUrlPrefix: String = getString("microservice.services.company-auth-frontend.continue-url-prefix")

  val feedbackSurveyUrl: String = s"${getUrlFor("feedback-survey")}"

  val signOutUrl: String = s"$caFrontendUrl/sign-out?continue=$feedbackSurveyUrl/HTS"

  val ggUserUrl: String =
    s"${getUrlFor("government-gateway-registration")}/government-gateway-registration-frontend?" +
      "accountType=individual&" +
      s"continue=${urlEncode(ggContinueUrlPrefix)}%2Fhelp-to-save%2Fcheck-eligibility&" +
      "origin=help-to-save-frontend&" +
      "registerForSa=skip"

  def ivJourneyResultUrl(journeyId: JourneyId): String = new URI(s"$ivJourneyResultUrl/${journeyId.Id}").toString

  val verifyEmailURL: String = s"${baseUrl("email-verification")}/email-verification/verification-requests"

  val linkTTLMinutes: Int = getInt("microservice.services.email-verification.linkTTLMinutes")

  val newApplicantContinueURL: String = s"$helpToSaveFrontendUrl/email-confirmed-callback"

  val accountHolderContinueURL: String = s"$helpToSaveFrontendUrl/account-home/email-confirmed-callback"

  val nsiManageAccountUrl: String = getUrlFor("nsi.manage-account")
  val nsiPayInUrl: String = getUrlFor("nsi.pay-in")

  val analyticsToken: String = getString("google-analytics.token")
  val analyticsHost: String = getString("google-analytics.host")
  val analyticsGovUkToken: String = getString("google-analytics.govuk-token")

  val contactFormServiceIdentifier: String = "HTS"

  val contactBaseUrl: String = getUrlFor("contact-frontend")
  val reportAProblemPartialUrl: String = s"$contactBaseUrl/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  val reportAProblemNonJSUrl: String = s"$contactBaseUrl/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"
  val betaFeedbackUrlNoAuth: String = s"$contactBaseUrl/contact/beta-feedback-unauthenticated?service=$contactFormServiceIdentifier"

  val govUkURL: String = getString("gov-uk.url.base")
  val govUkEligibilityInfoUrl: String = s"$govUkURL/eligibility"
  val govUkCallChargesUrl: String = getString("gov-uk.url.call-charges")
  val govUkDealingWithHRMCAdditionalNeedsUrl: String = getString("gov-uk.url.dealing-with-hmrc-additional-needs")
  val hmrcAppGuideURL: String = getString("gov-uk.url.hmrc-app-guide")

  val youtubeSavingsExplained: String = getString("youtube-embeds.savings-explained")
  val youtubeWhatBonuses: String = getString("youtube-embeds.what-bonuses")
  val youtubeHowWithdrawalsAffectBonuses: String = getString("youtube-embeds.how-withdrawals-affect-bonuses")

  val enableLanguageSwitching: Boolean = getBoolean("enableLanguageSwitching")

  object BankDetailsConfig {
    val sortCodeLength: Int = getInt("bank-details-validation.sort-code.length")
    val accountNumberLength: Int = getInt("bank-details-validation.account-number.length")
    val rollNumberMinLength: Int = getInt("bank-details-validation.roll-number.min-length")
    val rollNumberMaxLength: Int = getInt("bank-details-validation.roll-number.max-length")
    val accountNameMinLength: Int = getInt("bank-details-validation.account-name.min-length")
    val accountNameMaxLength: Int = getInt("bank-details-validation.account-name.max-length")
  }

  val mongoSessionExpireAfter: Duration = getDuration("mongodb.session.expireAfter")
}
