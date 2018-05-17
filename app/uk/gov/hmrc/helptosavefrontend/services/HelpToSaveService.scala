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

import javax.inject.Singleton
import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject}
import uk.gov.hmrc.helptosavefrontend.connectors.NSIProxyConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.connectors.{HelpToSaveConnector, NSIProxyConnector}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.account.{Account, AccountO}
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResult
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.util.{Email, Logging, Result}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveServiceImpl])
trait HelpToSaveService {

  def getUserEnrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EnrolmentStatus]

  def checkEligibility()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EligibilityCheckResult]

  def enrolUser()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def setITMPFlag()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def storeConfirmedEmail(email: Email)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def getConfirmedEmail()(implicit hv: HeaderCarrier, ec: ExecutionContext): Result[Option[String]]

  def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, SubmissionFailure, SubmissionSuccess]

  def isAccountCreationAllowed()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UserCapResponse]

  def updateUserCount()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def getAccount(nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[AccountO]

}

@Singleton
class HelpToSaveServiceImpl @Inject() (helpToSaveConnector: HelpToSaveConnector, nSIConnector: NSIProxyConnector) extends HelpToSaveService with Logging {

  def getUserEnrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EnrolmentStatus] =
    helpToSaveConnector.getUserEnrolmentStatus()

  def checkEligibility()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EligibilityCheckResult] =
    helpToSaveConnector.getEligibility()

  def enrolUser()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    helpToSaveConnector.enrolUser()

  def setITMPFlag()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    helpToSaveConnector.setITMPFlag()

  def storeConfirmedEmail(email: Email)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    helpToSaveConnector.storeEmail(email)

  def getConfirmedEmail()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Option[String]] =
    helpToSaveConnector.getEmail()

  def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, SubmissionFailure, SubmissionSuccess] =
    EitherT(nSIConnector.createAccount(userInfo).map[Either[SubmissionFailure, SubmissionSuccess]] {
      case success: SubmissionSuccess ⇒
        Right(success)
      case failure: SubmissionFailure ⇒
        Left(failure)
    })

  def isAccountCreationAllowed()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UserCapResponse] =
    helpToSaveConnector.isAccountCreationAllowed()

  def updateUserCount()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    helpToSaveConnector.updateUserCount()

  def getAccount(nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[AccountO] =
    helpToSaveConnector.getAccount(nino, correlationId)
}

