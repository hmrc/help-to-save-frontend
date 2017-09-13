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

package uk.gov.hmrc.helptosavefrontend.services

import javax.inject.Singleton

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject}
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.connectors.{HelpToSaveConnector, NSIConnector}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.util.{Email, Logging, NINO, Result}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveServiceImpl])
trait HelpToSaveService {

  def getUserEnrolmentStatus(nino: NINO)(implicit hc: HeaderCarrier): Result[EnrolmentStatus]

  def checkEligibility(nino: String)(implicit hc: HeaderCarrier): Result[EligibilityCheckResult]

  def enrolUser(nino: NINO)(implicit hc: HeaderCarrier): Result[Unit]

  def setITMPFlag(nino: NINO)(implicit hc: HeaderCarrier): Result[Unit]

  def storeConfirmedEmail(email: Email, nino: NINO)(implicit hv: HeaderCarrier): Result[Unit]

  def getConfirmedEmail(nino: NINO)(implicit hv: HeaderCarrier): Result[Option[String]]

  def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, SubmissionFailure, SubmissionSuccess]

}

@Singleton
class HelpToSaveServiceImpl @Inject() (helpToSaveConnector: HelpToSaveConnector, nSIConnector: NSIConnector) extends HelpToSaveService with Logging {

  def getUserEnrolmentStatus(nino: NINO)(implicit hc: HeaderCarrier): Result[EnrolmentStatus] =
    helpToSaveConnector.getUserEnrolmentStatus(nino)

  def checkEligibility(nino: String)(implicit hc: HeaderCarrier): Result[EligibilityCheckResult] =
    helpToSaveConnector.getEligibility(nino)

  def enrolUser(nino: NINO)(implicit hc: HeaderCarrier): Result[Unit] =
    helpToSaveConnector.enrolUser(nino)

  def setITMPFlag(nino: NINO)(implicit hc: HeaderCarrier): Result[Unit] =
    helpToSaveConnector.setITMPFlag(nino)

  def storeConfirmedEmail(email: Email, nino: NINO)(implicit hv: HeaderCarrier): Result[Unit] =
    helpToSaveConnector.storeEmail(email, nino)

  def getConfirmedEmail(nino: NINO)(implicit hv: HeaderCarrier): Result[Option[String]] =
    helpToSaveConnector.getEmail(nino)

  def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, SubmissionFailure, SubmissionSuccess] =
    EitherT(nSIConnector.createAccount(userInfo).map[Either[SubmissionFailure, SubmissionSuccess]] {
      case success: SubmissionSuccess ⇒
        logger.info(s"Successfully created an account for ${userInfo.nino}")
        Right(success)
      case failure: SubmissionFailure ⇒
        logger.error(s"Could not create an account for ${userInfo.nino} due to $failure")
        Left(failure)
    })
}

