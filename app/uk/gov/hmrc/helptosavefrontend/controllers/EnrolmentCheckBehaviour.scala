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

package uk.gov.hmrc.helptosavefrontend.controllers

import cats.instances.future._
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HtsContextWithNINO}
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Logging, NINO, NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait EnrolmentCheckBehaviour extends Logging {
  this: BaseController ⇒

  val frontendAppConfig: FrontendAppConfig
  val helpToSaveService: HelpToSaveService

  def checkIfAlreadyEnrolled(ifNotEnrolled:               () ⇒ Future[Result],
                             handleEnrolmentServiceError: String ⇒ Future[Result]
  )(implicit htsContext: HtsContextWithNINO, hc: HeaderCarrier, transformer: NINOLogMessageTransformer, ec: ExecutionContext): Future[Result] = {
    val nino = htsContext.nino

    helpToSaveService.getUserEnrolmentStatus()
      .leftSemiflatMap{ error ⇒
        logger.warn(s"Error while trying to check if user was already enrolled to HtS: $error", nino)
        handleEnrolmentServiceError(error)
      }
      .semiflatMap{
        case EnrolmentStatus.Enrolled(itmpHtSFlag) ⇒
          if (!itmpHtSFlag) { setItmpFlag(nino) }

          SeeOther(frontendAppConfig.nsiManageAccountUrl)

        case EnrolmentStatus.NotEnrolled ⇒
          ifNotEnrolled()
      }
      .merge
  }

  def checkIfAlreadyEnrolled(ifNotEnrolled: () ⇒ Future[Result])(
      implicit
      htsContext:  HtsContextWithNINO,
      hc:          HeaderCarrier,
      request:     Request[_],
      transformer: NINOLogMessageTransformer,
      ec:          ExecutionContext): Future[Result] =
    checkIfAlreadyEnrolled(ifNotEnrolled, _ ⇒ internalServerError())

  def checkIfEnrolled(ifNotEnrolled:               () ⇒ Future[Result],
                      handleEnrolmentServiceError: String ⇒ Future[Result],
                      ifEnrolled:                  () ⇒ Future[Result])(implicit htsContext: HtsContextWithNINO,
                                                                        hc:          HeaderCarrier,
                                                                        transformer: NINOLogMessageTransformer,
                                                                        ec:          ExecutionContext): Future[Result] = {
    val nino = htsContext.nino

    helpToSaveService.getUserEnrolmentStatus()
      .leftSemiflatMap{ error ⇒
        logger.warn(s"Error while trying to check if user was already enrolled to HtS: $error", nino)
        handleEnrolmentServiceError(error)
      }
      .semiflatMap{
        case EnrolmentStatus.Enrolled(itmpHtSFlag) ⇒
          if (!itmpHtSFlag) { setItmpFlag(nino) }
          ifEnrolled()

        case EnrolmentStatus.NotEnrolled ⇒
          ifNotEnrolled()
      }
      .merge
  }

  def setItmpFlag(nino: NINO)(implicit ec: ExecutionContext,
                              hc:          HeaderCarrier,
                              transformer: NINOLogMessageTransformer
  ): Unit =
    helpToSaveService.setITMPFlagAndUpdateMongo().value.onComplete {
      case Failure(e)        ⇒ logger.warn(s"Could not start process to set ITMP flag, future failed: $e", nino)
      case Success(Left(e))  ⇒ logger.warn(s"Could not start process to set ITMP flag: $e", nino)
      case Success(Right(_)) ⇒ logger.info(s"Process started to set ITMP flag", nino)
    }

}
