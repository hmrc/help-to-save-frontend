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
import uk.gov.hmrc.helptosavefrontend.repo.EnrolmentStore
import uk.gov.hmrc.helptosavefrontend.models.HtsContext
import uk.gov.hmrc.helptosavefrontend.services.EnrolmentService
import uk.gov.hmrc.helptosavefrontend.util.{Logging, NINO, toFuture}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

trait EnrolmentCheckBehaviour { this: FrontendController with Logging ⇒

  import EnrolmentCheckBehaviour._

  val enrolmentService: EnrolmentService

  def checkIfAlreadyEnrolled(ifNotEnrolled: NINO ⇒ Future[Result]
                            )(implicit htsContext: HtsContext, hc: HeaderCarrier): Future[Result] = {
    val enrolled = for {
      nino ← EitherT.fromOption[Future](htsContext.nino, NoNINO)
      enrolmentStatus ← enrolmentService.getUserEnrolmentStatus(nino).leftMap[EnrolmentCheckError](e ⇒ EnrolmentServiceError(nino, e))
    } yield (nino, enrolmentStatus)


    enrolled.semiflatMap {
      case (nino, EnrolmentStore.Enrolled(itmpHtSFlag)) ⇒
        // if the user is enrolled but the itmp flag is not set then just
        // start the process to set the itmp flag here without worrying about the result
        if (!itmpHtSFlag) {
          enrolmentService.setITMPFlag(nino).fold(
            e ⇒ logger.warn(s"Could not start process to set ITMP flag for user $nino: $e"),
            _ ⇒ logger.info(s"Process started to set ITMP flag for user $nino")
          )
        }
        Ok("You've already got an account - yay!")

      case (nino, EnrolmentStore.NotEnrolled) ⇒
        ifNotEnrolled(nino)
    }.leftMap(handleError)
      .value
      .map(_.merge)
  }

  private def handleError(enrolmentCheckError: EnrolmentCheckError): Result = enrolmentCheckError match {
    case NoNINO ⇒
      logger.warn("Could not get NINO")
      InternalServerError

    case EnrolmentServiceError(nino, message) ⇒
      logger.warn(s"Error while trying to check if user $nino was already enrolled to HtS: $message")
      InternalServerError
  }

}

object EnrolmentCheckBehaviour {

  private sealed trait EnrolmentCheckError

  private case object NoNINO extends EnrolmentCheckError

  private case class EnrolmentServiceError(nino: NINO, message: String) extends EnrolmentCheckError

}
