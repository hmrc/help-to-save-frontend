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

package uk.gov.hmrc.helptosavefrontend.connectors

import java.util.UUID

import cats.data.EitherT
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl._
import uk.gov.hmrc.helptosavefrontend.http.HttpClient.HttpClientOps
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.account.Account
import uk.gov.hmrc.helptosavefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResultType}
import uk.gov.hmrc.helptosavefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavefrontend.models.userinfo.{MissingUserInfo, NSIPayload}
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.{Email, Result, base64Encode, maskNino}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def getEligibility()(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, EligibilityCheckResultType]

  def getUserEnrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EnrolmentStatus]

  def setITMPFlagAndUpdateMongo()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def storeEmail(email: Email)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def getEmail()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Option[String]]

  def isAccountCreationAllowed()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UserCapResponse]

  def getAccount(nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Account]

  def createAccount(createAccountRequest: CreateAccountRequest)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[HttpResponse]

  def updateEmail(userInfo: NSIPayload)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[HttpResponse]

  def validateBankDetails(barsRequest: BarsRequest)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[HttpResponse]

}

@Singleton
class HelpToSaveConnectorImpl @Inject() (http: HttpClient)(implicit frontendAppConfig: FrontendAppConfig) extends HelpToSaveConnector {

  private val helpToSaveUrl: String = frontendAppConfig.baseUrl("help-to-save")

  private val eligibilityURL =
    s"$helpToSaveUrl/help-to-save/eligibility-check"

  private val enrolmentStatusURL =
    s"$helpToSaveUrl/help-to-save/enrolment-status"

  private val enrolUserURL =
    s"$helpToSaveUrl/help-to-save/enrol-user"

  private val setITMPFlagURL =
    s"$helpToSaveUrl/help-to-save/set-itmp-flag"

  private val storeEmailURL =
    s"$helpToSaveUrl/help-to-save/store-email"

  private val getEmailURL =
    s"$helpToSaveUrl/help-to-save/get-email"

  private val accountCreateAllowedURL =
    s"$helpToSaveUrl/help-to-save/account-create-allowed"

  private val createAccountURL =
    s"$helpToSaveUrl/help-to-save/create-account"

  private val updateEmailURL =
    s"$helpToSaveUrl/help-to-save/update-email"

  private val validateBankDetailsURL =
    s"$helpToSaveUrl/help-to-save/validate-bank-details"

  private def getAccountUrl(nino: String) = s"$helpToSaveUrl/help-to-save/$nino/account"

  private def getAccountQueryParams(correlationId: UUID): Map[String, String] =
    Map("correlationId" → correlationId.toString, "systemId" → "help-to-save-frontend")

  private val emptyQueryParameters: Map[String, String] = Map.empty[String, String]

  def getEligibility()(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, EligibilityCheckResultType] =
    handleGet(
      eligibilityURL,
      emptyQueryParameters,
      _.parseJSON[EligibilityCheckResponse]().flatMap(toEligibilityCheckResult),
      "check eligibility",
      identity
    )

  def getUserEnrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EnrolmentStatus] =
    handleGet(enrolmentStatusURL, emptyQueryParameters, _.parseJSON[EnrolmentStatus](), "get user enrolment status", identity)

  def enrolUser()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    handleGet(enrolUserURL, emptyQueryParameters, _ ⇒ Right(()), "enrol users", identity)

  def setITMPFlagAndUpdateMongo()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    handleGet(setITMPFlagURL, emptyQueryParameters, _ ⇒ Right(()), "set ITMP flag and update mongo", identity)

  def storeEmail(email: Email)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] = {
    handleGet(storeEmailURL, Map("email" -> new String(base64Encode(email))), _ ⇒ Right(()), "store email", identity)
  }

  def getEmail()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Option[String]] =
    handleGet(getEmailURL, emptyQueryParameters, _.parseJSON[GetEmailResponse]().map(_.email), "get email", identity)

  def isAccountCreationAllowed()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UserCapResponse] =
    handleGet(accountCreateAllowedURL, emptyQueryParameters, _.parseJSON[UserCapResponse](), "account creation allowed", identity)

  def getAccount(nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Account] =
    handleGet(getAccountUrl(nino), getAccountQueryParams(correlationId), _.parseJSON[Account](), "get Account", identity)

  private def handleGet[A, B](url:             String,
                              queryParameters: Map[String, String],
                              ifHTTP200:       HttpResponse ⇒ Either[B, A],
                              description:     ⇒ String,
                              toError:         String ⇒ B)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, B, A] =
    handle(http.get(url, queryParameters), ifHTTP200, description, toError)

  private def handle[A, B](resF:        Future[HttpResponse],
                           ifHTTP200:   HttpResponse ⇒ Either[B, A],
                           description: ⇒ String,
                           toError:     String ⇒ B)(implicit ec: ExecutionContext) = {
    EitherT(resF.map { response ⇒
      if (response.status == 200) {
        ifHTTP200(response)
      } else {
        Left(toError(s"Call to $description came back with status ${response.status}. Body was ${maskNino(response.body)}"))
      }
    }.recover {
      case NonFatal(t) ⇒ Left(toError(s"Call to $description failed: ${t.getMessage}"))
    })
  }

  // scalastyle:off magic.number
  private def toEligibilityCheckResult(response: EligibilityCheckResponse): Either[String, EligibilityCheckResultType] =
    response.eligibilityCheckResult.resultCode match {
      case 1 ⇒ Right(EligibilityCheckResultType.Eligible(response))
      case 2 ⇒ Right(EligibilityCheckResultType.Ineligible(response))
      case 3 ⇒ Right(EligibilityCheckResultType.AlreadyHasAccount(response))
      case 4 ⇒ Left(s"Error while checking eligibility. Received result code 4 ${response.eligibilityCheckResult.result}) " +
        s"with reason code ${response.eligibilityCheckResult.reasonCode} (${response.eligibilityCheckResult.reason})")
      case other ⇒ Left(s"Could not parse eligibility result code '$other'. Response was '$response'")
    }

  override def createAccount(createAccountRequest: CreateAccountRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.post(createAccountURL, createAccountRequest)

  override def updateEmail(userInfo: NSIPayload)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.put(updateEmailURL, userInfo)

  override def validateBankDetails(barsRequest: BarsRequest)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[HttpResponse] =
    http.post(validateBankDetailsURL, barsRequest)
}

object HelpToSaveConnectorImpl {

  private[connectors] case class MissingUserInfoSet(missingInfo: Set[MissingUserInfo])

  private[connectors] case class GetEmailResponse(email: Option[String])

  private[connectors] object GetEmailResponse {
    implicit val format: Format[GetEmailResponse] = Json.format[GetEmailResponse]
  }

}
