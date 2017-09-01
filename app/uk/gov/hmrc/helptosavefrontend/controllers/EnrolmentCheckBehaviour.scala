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

package uk.gov.hmrc.helptosavefrontend.controllers

import cats.data.EitherT
import cats.instances.future._
import play.api.mvc.Result
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HtsContext}
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.{Logging, NINO, toFuture}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait EnrolmentCheckBehaviour {
  this: FrontendController with Logging ⇒

  import EnrolmentCheckBehaviour._

  val helpToSaveService: HelpToSaveService

  def checkIfAlreadyEnrolled(ifNotEnrolled:               NINO ⇒ Future[Result],
                             handleEnrolmentServiceError: EnrolmentServiceError ⇒ Future[Result] = _ ⇒ InternalServerError
  )(implicit htsContext: HtsContext, hc: HeaderCarrier): Future[Result] = {
    val enrolled: EitherT[Future, EnrolmentCheckError, (String, EnrolmentStatus)] = for {
      nino ← EitherT.fromOption[Future](htsContext.nino, NoNINO)
      enrolmentStatus ← helpToSaveService.getUserEnrolmentStatus(nino).leftMap[EnrolmentCheckError](e ⇒ EnrolmentServiceError(nino, e))
    } yield (nino, enrolmentStatus)

    enrolled.fold[Future[Result]](initialError ⇒
      handleError(initialError, handleEnrolmentServiceError), {
      case (nino, EnrolmentStatus.Enrolled(itmpHtSFlag)) ⇒
        // if the user is enrolled but the itmp flag is not set then just
        // start the process to set the itmp flag here without worrying about the result
        if (!itmpHtSFlag) {
          helpToSaveService.setITMPFlag(nino).value.onComplete{
            case Failure(e)        ⇒ logger.warn(s"For NINO [$nino]: Could not start process to set ITMP flag, future failed: $e")
            case Success(Left(e))  ⇒ logger.warn(s"For NINO [$nino]: Could not start process to set ITMP flag: $e")
            case Success(Right(_)) ⇒ logger.info(s"For NINO [$nino]: Process started to set ITMP flag")
          }
        }

        Ok("You've already got an account - yay!")

      case (nino, EnrolmentStatus.NotEnrolled) ⇒
        ifNotEnrolled(nino)
    }
    ).flatMap(identity)
  }

  private def handleError(enrolmentCheckError:         EnrolmentCheckError,
                          handleEnrolmentServiceError: EnrolmentServiceError ⇒ Future[Result]
  ): Future[Result] = enrolmentCheckError match {
    case NoNINO ⇒
      logger.warn("Could not get NINO")
      InternalServerError

    case e @ EnrolmentServiceError(nino, message) ⇒
      logger.warn(s"For NINO [$nino]: Error while trying to check if user was already enrolled to HtS: $message")
      handleEnrolmentServiceError(e)

  }

}

object EnrolmentCheckBehaviour {

  sealed trait EnrolmentCheckError

  case object NoNINO extends EnrolmentCheckError

  case class EnrolmentServiceError(nino: NINO, message: String) extends EnrolmentCheckError

}
