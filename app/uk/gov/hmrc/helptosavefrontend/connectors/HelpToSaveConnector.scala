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

import java.util.Base64

import cats.data.EitherT
import cats.syntax.either._
import cats.instances.future._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig._
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl.EligibilityCheckResponse
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl.URLS._
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.{Email, NINO, Result}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def getEligibility(nino: NINO)(implicit hc: HeaderCarrier): EitherT[Future, String, EligibilityCheckResult]

  def getUserEnrolmentStatus(nino: NINO)(implicit hc: HeaderCarrier): Result[EnrolmentStatus]

  def enrolUser(nino: NINO)(implicit hc: HeaderCarrier): Result[Unit]

  def setITMPFlag(nino: NINO)(implicit hc: HeaderCarrier): Result[Unit]

  def storeEmail(email: Email, nino: NINO)(implicit hc: HeaderCarrier): Result[Unit]

}

@Singleton
class HelpToSaveConnectorImpl @Inject() (http: WSHttp)(implicit ec: ExecutionContext) extends HelpToSaveConnector {

  val base64Encoder: Base64.Encoder = Base64.getEncoder

  override def getEligibility(nino: NINO)(implicit hc: HeaderCarrier): EitherT[Future, String, EligibilityCheckResult] = {
    EitherT.right[Future, String, HttpResponse](http.get(eligibilityURL(nino)))
      .subflatMap { response ⇒
        if (response.status == 200) {
          response.parseJson[EligibilityCheckResponse].flatMap(r ⇒ toEligibilityCheckResponse(r))
        } else {
          Left(s"Call to check eligibility came back with status ${response.status}")
        }
      }
  }

  def getUserEnrolmentStatus(nino: NINO)(implicit hc: HeaderCarrier): Result[EnrolmentStatus] =
    handle(enrolmentStatusURL(nino), _.parseJson[EnrolmentStatus], "get user enrolment status", identity)

  def enrolUser(nino: NINO)(implicit hc: HeaderCarrier): Result[Unit] =
    handle(enrolUserURL(nino), _ ⇒ Right(()), "enrol users", identity)

  def setITMPFlag(nino: NINO)(implicit hc: HeaderCarrier): Result[Unit] =
    handle(setITMPFlagURL(nino), _ ⇒ Right(()), "set ITMP flag", identity)

  def storeEmail(email: Email, nino: NINO)(implicit hc: HeaderCarrier): Result[Unit] = {
    val encodedEmail = new String(base64Encoder.encode(email.getBytes()))
    handle(storeEmailURL(encodedEmail, nino), _ ⇒ Right(()), "store email", identity)
  }

  private def handle[A, B](url:         String,
                           ifHTTP200:   HttpResponse ⇒ Either[B, A],
                           description: ⇒ String,
                           toError:     String ⇒ B
  )(implicit hc: HeaderCarrier): EitherT[Future, B, A] =
    EitherT(http.get(url).map { response ⇒
      if (response.status == 200) {
        ifHTTP200(response)
      } else {
        Left(toError(s"Call to $description came back with status ${response.status}"))
      }
    }.recover {
      case NonFatal(t) ⇒ Left(toError(s"Call to $description failed: ${t.getMessage}"))
    })

  private def eligibilityURL(nino: NINO) = s"$helpToSaveUrl/help-to-save/eligibility-check?nino=$nino"

  private def toEligibilityCheckResponse(eligibilityCheckResponse: EligibilityCheckResponse): Either[String, EligibilityCheckResult] = {
    val reasonInt = eligibilityCheckResponse.reason

    eligibilityCheckResponse.result match {
      case 1 ⇒
        // user is eligible
        Either.fromOption(
          EligibilityReason.fromInt(reasonInt).map(r ⇒ EligibilityCheckResult(Right(r))),
          s"Could not parse ineligibility reason '$reasonInt'")

      case 2 ⇒
        // user is ineligible
        Either.fromOption(
          IneligibilityReason.fromInt(reasonInt).map(r ⇒ EligibilityCheckResult(Left(r))),
          s"Could not parse eligibility reason '$reasonInt'")

      case other ⇒
        Left(s"Could not parse eligibility result '$other'")

    }
  }

}

object HelpToSaveConnectorImpl {

  private[connectors] object URLS {
    def eligibilityURL(nino: NINO) =
      s"$helpToSaveUrl/help-to-save/eligibility-check?nino=$nino"

    def userInformationURL(nino: NINO, userDetailsURI: String) =
      s"$helpToSaveUrl/help-to-save/user-information?nino=$nino&userDetailsURI=${encoded(userDetailsURI)}"

    def enrolmentStatusURL(nino: NINO) =
      s"$helpToSaveUrl/help-to-save/enrolment-status?nino=$nino"

    def enrolUserURL(nino: NINO) =
      s"$helpToSaveUrl/help-to-save/enrol-user?nino=$nino"

    def setITMPFlagURL(nino: NINO) =
      s"$helpToSaveUrl/help-to-save/set-itmp-flag?nino=$nino"

    def storeEmailURL(email: Email, nino: NINO) =
      s"$helpToSaveUrl/help-to-save/store-email?email=$email&nino=$nino"
  }

  private[connectors] case class MissingUserInfoSet(missingInfo: Set[MissingUserInfo])

  private[connectors] object MissingUserInfoSet {
    implicit val missingUserInfoSetFormat: Format[MissingUserInfoSet] =
      Json.format[MissingUserInfoSet]
  }

  private[connectors] case class EligibilityCheckResponse(result: Int, reason: Int)

  private[connectors] object EligibilityCheckResponse {

    implicit val format: Format[EligibilityCheckResponse] = Json.format[EligibilityCheckResponse]

  }

}
