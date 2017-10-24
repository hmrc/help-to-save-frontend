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

package uk.gov.hmrc.helptosavefrontend.connectors

import cats.data.EitherT
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig._
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl.GetEmailResponse
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl.URLS._
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.{Email, Result}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def getEligibility()(implicit hc: HeaderCarrier): EitherT[Future, String, EligibilityCheckResult]

  def getUserEnrolmentStatus()(implicit hc: HeaderCarrier): Result[EnrolmentStatus]

  def enrolUser()(implicit hc: HeaderCarrier): Result[Unit]

  def setITMPFlag()(implicit hc: HeaderCarrier): Result[Unit]

  def storeEmail(email: Email)(implicit hc: HeaderCarrier): Result[Unit]

  def getEmail()(implicit hc: HeaderCarrier): Result[Option[String]]

  def isAccountCreationAllowed()(implicit hc: HeaderCarrier): Result[Boolean]

  def updateUserCount()(implicit hc: HeaderCarrier): Result[Unit]

}

@Singleton
class HelpToSaveConnectorImpl @Inject() (http: WSHttp)(implicit ec: ExecutionContext) extends HelpToSaveConnector {

  def getEligibility()(implicit hc: HeaderCarrier): EitherT[Future, String, EligibilityCheckResult] =
    handleGet(
      eligibilityURL,
      _.parseJson[EligibilityCheckResponse].flatMap(toEligibilityCheckResult),
      "check eligibility",
      identity
    )

  def getUserEnrolmentStatus()(implicit hc: HeaderCarrier): Result[EnrolmentStatus] =
    handleGet(enrolmentStatusURL, _.parseJson[EnrolmentStatus], "get user enrolment status", identity)

  def enrolUser()(implicit hc: HeaderCarrier): Result[Unit] =
    handleGet(enrolUserURL, _ ⇒ Right(()), "enrol users", identity)

  def setITMPFlag()(implicit hc: HeaderCarrier): Result[Unit] =
    handleGet(setITMPFlagURL, _ ⇒ Right(()), "set ITMP flag", identity)

  def storeEmail(email: Email)(implicit hc: HeaderCarrier): Result[Unit] = {
    val encodedEmail = new String(base64Encode(email))
    handleGet(storeEmailURL(encodedEmail), _ ⇒ Right(()), "store email", identity)
  }

  def getEmail()(implicit hc: HeaderCarrier): Result[Option[String]] =
    handleGet(getEmailURL, _.parseJson[GetEmailResponse].map(_.email), "get email", identity)

  def isAccountCreationAllowed()(implicit hc: HeaderCarrier): Result[Boolean] = {
    handleGet(accountCreateAllowedURL, _.parseJson[Boolean], "account creation allowed", identity)
  }

  def updateUserCount()(implicit hc: HeaderCarrier): Result[Unit] = {
    handlePost(updateUserCountURL, "", _ ⇒ Right(()), "update user count", identity)
  }

  private def handlePost[A, B](url:         String,
                               body:        String,
                               ifHTTP200:   HttpResponse ⇒ Either[B, A],
                               description: ⇒ String,
                               toError:     String ⇒ B)(implicit hc: HeaderCarrier): EitherT[Future, B, A] =
    handle(http.post(url, body), ifHTTP200, description, toError)

  private def handleGet[A, B](url:         String,
                              ifHTTP200:   HttpResponse ⇒ Either[B, A],
                              description: ⇒ String,
                              toError:     String ⇒ B)(implicit hc: HeaderCarrier): EitherT[Future, B, A] =
    handle(http.get(url), ifHTTP200, description, toError)

  private def handle[A, B](resF:        Future[HttpResponse],
                           ifHTTP200:   HttpResponse ⇒ Either[B, A],
                           description: ⇒ String,
                           toError:     String ⇒ B) = {
    EitherT(resF.map { response ⇒
      if (response.status == 200) {
        ifHTTP200(response)
      } else {
        Left(toError(s"Call to $description came back with status ${response.status}. Body was ${response.body}"))
      }
    }.recover {
      case NonFatal(t) ⇒ Left(toError(s"Call to $description failed: ${t.getMessage}"))
    })
  }

  private val eligibilityURL = s"$helpToSaveUrl/help-to-save/eligibility-check"

  // scalastyle:off magic.number
  private def toEligibilityCheckResult(response: EligibilityCheckResponse): Either[String, EligibilityCheckResult] = {
    response.resultCode match {
      case 1     ⇒ Right(EligibilityCheckResult.Eligible(response))
      case 2     ⇒ Right(EligibilityCheckResult.Ineligible(response))
      case 3     ⇒ Right(EligibilityCheckResult.AlreadyHasAccount(response))
      case other ⇒ Left(s"Could not parse eligibility result code '$other'. Response was '$response'")

    }
  }
}

object HelpToSaveConnectorImpl {

  private[connectors] object URLS {
    val eligibilityURL =
      s"$helpToSaveUrl/help-to-save/eligibility-check"

    val enrolmentStatusURL =
      s"$helpToSaveUrl/help-to-save/enrolment-status"

    val enrolUserURL =
      s"$helpToSaveUrl/help-to-save/enrol-user"

    val setITMPFlagURL =
      s"$helpToSaveUrl/help-to-save/set-itmp-flag"

    def storeEmailURL(email: Email) =
      s"$helpToSaveUrl/help-to-save/store-email?email=$email"

    val getEmailURL =
      s"$helpToSaveUrl/help-to-save/get-email"

    val accountCreateAllowedURL =
      s"$helpToSaveUrl/help-to-save/account-create-allowed"

    val updateUserCountURL =
      s"$helpToSaveUrl/help-to-save/update-user-count"
  }

  private[connectors] case class MissingUserInfoSet(missingInfo: Set[MissingUserInfo])

  private[connectors] case class GetEmailResponse(email: Option[String])

  private[connectors] object GetEmailResponse {
    implicit val format: Format[GetEmailResponse] = Json.format[GetEmailResponse]
  }
}
