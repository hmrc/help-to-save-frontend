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

package uk.gov.hmrc.helptosavefrontend.services

import java.util.UUID

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject}
import javax.inject.Singleton
import play.api.http.Status
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.account.{Account, AccountNumber}
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResultType
import uk.gov.hmrc.helptosavefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIPayload
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveServiceImpl.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.{Email, Logging, Result, maskNino}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@ImplementedBy(classOf[HelpToSaveServiceImpl])
trait HelpToSaveService {

  type CreateAccountResultType = EitherT[Future, SubmissionFailure, SubmissionSuccess]

  def getUserEnrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EnrolmentStatus]

  def checkEligibility()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EligibilityCheckResultType]

  def setITMPFlagAndUpdateMongo()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def storeConfirmedEmail(email: Email)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def getConfirmedEmail()(implicit hv: HeaderCarrier, ec: ExecutionContext): Result[Option[String]]

  def createAccount(createAccountRequest: CreateAccountRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): CreateAccountResultType

  def isAccountCreationAllowed()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UserCapResponse]

  def getAccount(nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Account]

  def updateEmail(userInfo: NSIPayload)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def validateBankDetails(barsRequest: BarsRequest)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Boolean]

}

@Singleton
class HelpToSaveServiceImpl @Inject() (helpToSaveConnector: HelpToSaveConnector) extends HelpToSaveService with Logging {

  def getUserEnrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EnrolmentStatus] =
    helpToSaveConnector.getUserEnrolmentStatus()

  def checkEligibility()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EligibilityCheckResultType] =
    helpToSaveConnector.getEligibility()

  def setITMPFlagAndUpdateMongo()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    helpToSaveConnector.setITMPFlagAndUpdateMongo()

  def storeConfirmedEmail(email: Email)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    helpToSaveConnector.storeEmail(email)

  def getConfirmedEmail()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Option[String]] =
    helpToSaveConnector.getEmail()

  def createAccount(createAccountRequest: CreateAccountRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): CreateAccountResultType =
    EitherT(helpToSaveConnector.createAccount(createAccountRequest)
      .map[Either[SubmissionFailure, SubmissionSuccess]] { response ⇒

        response.status match {
          case Status.CREATED ⇒
            response.parseJSON[AccountNumber]().fold[Either[SubmissionFailure, SubmissionSuccess]](
              e ⇒ Left(SubmissionFailure(None, "Couldn't parse account number JSON", e)),
              account ⇒ Right(SubmissionSuccess(Some(account)))
            )

          case Status.CONFLICT ⇒
            Right(SubmissionSuccess(None))

          case _ ⇒
            Left(handleError(response))
        }
      }.recover {
        case e ⇒
          Left(SubmissionFailure(None, "Encountered error while trying to create account", e.getMessage))
      }
    )

  def isAccountCreationAllowed()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UserCapResponse] =
    helpToSaveConnector.isAccountCreationAllowed()

  def getAccount(nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Account] =
    helpToSaveConnector.getAccount(nino, correlationId)

  override def updateEmail(userInfo: NSIPayload)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    EitherT(helpToSaveConnector.updateEmail(userInfo).map[Either[String, Unit]] { response ⇒

      response.status match {
        case Status.OK ⇒
          Right(())

        case other ⇒
          Left(s"Received unexpected status $other from NS&I proxy while trying to update email. Body was ${maskNino(response.body)}")

      }
    }.recover {
      case e ⇒
        Left(s"Encountered error while trying to update email: ${e.getMessage}")
    }
    )

  override def validateBankDetails(barsRequest: BarsRequest)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Boolean] =
    EitherT(helpToSaveConnector.validateBankDetails(barsRequest).map[Either[String, Boolean]] { response ⇒
      response.status match {
        case Status.OK ⇒
          Try((response.json \ "isValid").as[Boolean]) match {
            case Success(isvalid) ⇒ Right(isvalid)
            case Failure(error)   ⇒ Left(s"couldn't parse bars response from BE, error=${error.getMessage}. Body was ${maskNino(response.body)}")
          }

        case other ⇒
          Left(s"Received unexpected status $other from BARS validation. Body was ${maskNino(response.body)}")
      }
    }
    )

  private def handleError(response: HttpResponse): SubmissionFailure = {
    response.parseJSON[SubmissionFailure]() match {
      case Right(submissionFailure) ⇒ submissionFailure
      case Left(error)              ⇒ SubmissionFailure(None, "", error)
    }
  }
}

object HelpToSaveServiceImpl {

  sealed trait SubmissionResult

  case class SubmissionSuccess(accountNumber: Option[AccountNumber]) extends SubmissionResult

  implicit val submissionSuccessFormat: Format[SubmissionSuccess] = Json.format[SubmissionSuccess]

  case class SubmissionFailure(errorMessageId: Option[String], errorMessage: String, errorDetail: String) extends SubmissionResult

  implicit val submissionFailureFormat: Format[SubmissionFailure] = Json.format[SubmissionFailure]
}

