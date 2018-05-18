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
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAppConfig, WSHttp}
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl.GetEmailResponse
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.account.AccountO
import uk.gov.hmrc.helptosavefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.MissingUserInfo
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.{Email, Result, base64Encode, maskNino}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def getEligibility()(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, EligibilityCheckResult]

  def getUserEnrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EnrolmentStatus]

  def enrolUser()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def setITMPFlagAndUpdateMongo()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def storeEmail(email: Email)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def getEmail()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Option[String]]

  def isAccountCreationAllowed()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UserCapResponse]

  def updateUserCount()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def getAccount(nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[AccountO]

}

@Singleton
class HelpToSaveConnectorImpl @Inject() (http: WSHttp)(implicit frontendAppConfig: FrontendAppConfig) extends HelpToSaveConnector {

  private val helpToSaveUrl: String = frontendAppConfig.baseUrl("help-to-save")

  private val eligibilityURL =
    s"$helpToSaveUrl/help-to-save/eligibility-check"

  private val enrolmentStatusURL =
    s"$helpToSaveUrl/help-to-save/enrolment-status"

  private val enrolUserURL =
    s"$helpToSaveUrl/help-to-save/enrol-user"

  private val setITMPFlagURL =
    s"$helpToSaveUrl/help-to-save/set-itmp-flag"

  private def storeEmailURL(email: Email) =
    s"$helpToSaveUrl/help-to-save/store-email?email=$email"

  private val getEmailURL =
    s"$helpToSaveUrl/help-to-save/get-email"

  private val accountCreateAllowedURL =
    s"$helpToSaveUrl/help-to-save/account-create-allowed"

  private val updateUserCountURL =
    s"$helpToSaveUrl/help-to-save/update-user-count"

  private def getAccountURL(nino: String, correlationId: UUID) =
    s"$helpToSaveUrl/help-to-save/nsi-account?nino=$nino&correlationId=$correlationId&version=V1.0&systemId=help-to-save-frontend"

  def getEligibility()(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, EligibilityCheckResult] =
    handleGet(
      eligibilityURL,
      _.parseJSON[EligibilityCheckResponse]().flatMap(toEligibilityCheckResult),
      "check eligibility",
      identity
    )

  def getUserEnrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EnrolmentStatus] =
    handleGet(enrolmentStatusURL, _.parseJSON[EnrolmentStatus](), "get user enrolment status", identity)

  def enrolUser()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    handleGet(enrolUserURL, _ ⇒ Right(()), "enrol users", identity)

  def setITMPFlagAndUpdateMongo()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    handleGet(setITMPFlagURL, _ ⇒ Right(()), "set ITMP flag", identity)

  def storeEmail(email: Email)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] = {
    val encodedEmail = new String(base64Encode(email))
    handleGet(storeEmailURL(encodedEmail), _ ⇒ Right(()), "store email", identity)
  }

  def getEmail()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Option[String]] =
    handleGet(getEmailURL, _.parseJSON[GetEmailResponse]().map(_.email), "get email", identity)

  def isAccountCreationAllowed()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UserCapResponse] =
    handleGet(accountCreateAllowedURL, _.parseJSON[UserCapResponse](), "account creation allowed", identity)

  def updateUserCount()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    handlePost(updateUserCountURL, "", _ ⇒ Right(()), "update user count", identity)

  def getAccount(nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[AccountO] =
    handleGet(getAccountURL(nino, correlationId), _.parseJSON[AccountO](), "get Account", identity)

  private def handlePost[A, B](url:         String,
                               body:        String,
                               ifHTTP200:   HttpResponse ⇒ Either[B, A],
                               description: ⇒ String,
                               toError:     String ⇒ B)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, B, A] =
    handle(http.post(url, body), ifHTTP200, description, toError)

  private def handleGet[A, B](url:         String,
                              ifHTTP200:   HttpResponse ⇒ Either[B, A],
                              description: ⇒ String,
                              toError:     String ⇒ B)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, B, A] =
    handle(http.get(url), ifHTTP200, description, toError)

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
  private def toEligibilityCheckResult(response: EligibilityCheckResponse): Either[String, EligibilityCheckResult] =
    response.resultCode match {
      case 1     ⇒ Right(EligibilityCheckResult.Eligible(response))
      case 2     ⇒ Right(EligibilityCheckResult.Ineligible(response))
      case 3     ⇒ Right(EligibilityCheckResult.AlreadyHasAccount(response))
      case 4     ⇒ Left(s"Error while checking eligibility. Received result code 4 ${response.result}) with reason code ${response.reasonCode} (${response.reason})")
      case other ⇒ Left(s"Could not parse eligibility result code '$other'. Response was '$response'")
    }
}

object HelpToSaveConnectorImpl {

  private[connectors] case class MissingUserInfoSet(missingInfo: Set[MissingUserInfo])

  private[connectors] case class GetEmailResponse(email: Option[String])

  private[connectors] object GetEmailResponse {
    implicit val format: Format[GetEmailResponse] = Json.format[GetEmailResponse]
  }

}
