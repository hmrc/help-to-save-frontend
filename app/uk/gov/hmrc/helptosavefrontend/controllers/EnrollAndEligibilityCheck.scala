/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.mvc.{Request, Result}
import uk.gov.hmrc.helptosavefrontend.models.eligibility.IneligibilityReason
import uk.gov.hmrc.helptosavefrontend.models.{HTSSession, HtsContextWithNINO}
import uk.gov.hmrc.helptosavefrontend.util.{NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait EnrollAndEligibilityCheck extends SessionBehaviour with EnrolmentCheckBehaviour {
  this: BaseController ⇒

  def checkIfAlreadyEnrolledAndDoneEligibilityChecks(ifNotEnrolled: HTSSession ⇒ Future[Result])(
    implicit htsContext: HtsContextWithNINO,
    hc: HeaderCarrier,
    transformer: NINOLogMessageTransformer,
    ec: ExecutionContext,
    request: Request[_]
  ): Future[Result] =
    checkIfAlreadyEnrolled { () ⇒
      checkSession(
        SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
      ) { session ⇒
        session.eligibilityCheckResult.fold[Future[Result]](
          SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
        )(
          _.fold[Future[Result]](
            { ineligibleReason ⇒
              val ineligibilityType = IneligibilityReason.fromIneligible(ineligibleReason)
              val threshold = ineligibleReason.value.threshold

              ineligibilityType.fold {
                logger.warn(s"Could not parse ineligibility reason : $ineligibleReason")
                toFuture(internalServerError())
              } { i ⇒
                toFuture(SeeOther(routes.EligibilityCheckController.getIsNotEligible().url))
              }
            },
            _ ⇒ ifNotEnrolled(session)
          )
        )
      }
    }

}
