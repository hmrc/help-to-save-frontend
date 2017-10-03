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
import cats.syntax.either._
import play.api.libs.json.{JsValue, Reads, Writes}
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.connectors.SessionCacheConnector
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HTSSession}
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait EnrolmentAndEligibilityCheckBehaviour {
  this: AuthSupport ⇒

  val mockSessionCacheConnector: SessionCacheConnector = mock[SessionCacheConnector]

  val mockHelpToSaveService = mock[HelpToSaveService]

  def mockSessionCacheConnectorPut(expectedSession: HTSSession)(result: Either[String, Unit]): Unit =
    (mockSessionCacheConnector.put(_: HTSSession)(_: Writes[HTSSession], _: HeaderCarrier, _: ExecutionContext))
      .expects(expectedSession, *, *, *)
      .returning(EitherT.fromEither[Future](result.map(_ ⇒ CacheMap("1", Map.empty[String, JsValue]))))

  def mockSessionCacheConnectorGet(result: Either[String, Option[HTSSession]]): Unit =
    (mockSessionCacheConnector.get(_: Reads[HTSSession], _: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEnrolmentCheck()(result: Either[String, EnrolmentStatus]): Unit =
    (mockHelpToSaveService.getUserEnrolmentStatus()(_: HeaderCarrier))
      .expects(*)
      .returning(EitherT.fromEither[Future](result))

  def mockWriteITMPFlag()(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.setITMPFlag()(_: HeaderCarrier))
      .expects(*)
      .returning(EitherT.fromEither[Future](result))

  def commonEnrolmentAndSessionBehaviour(getResult:               () ⇒ Future[Result], // scalastyle:ignore method.length
                                         testRedirectOnNoSession: Boolean             = true,
                                         testEnrolmentCheckError: Boolean             = true): Unit = {

    "redirect to NS&I if the user is already enrolled" in {
      inSequence {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
        mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(itmpHtSFlag = true)))
      }

      val result = getResult()
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.NSIController.goToNSI().url)
    }

    "redirect to NS&I if the user is already enrolled and set the ITMP flag " +
      "if it has not already been set" in {
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(itmpHtSFlag = false)))
          mockWriteITMPFlag()(Right(()))
        }

        val result = getResult()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.NSIController.goToNSI().url)
      }

    "redirect to NS&I if the user is already enrolled even if there is an " +
      "setting the ITMP flag" in {
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(itmpHtSFlag = false)))
          mockWriteITMPFlag()(Left(""))
        }

        val result = getResult()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.NSIController.goToNSI().url)
      }

    if (testRedirectOnNoSession) {
      "redirect to the eligibility checks if there is no session data for the user" in {
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(None))
        }

        val result = getResult()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }
    }

    "return an error" when {

      if (testEnrolmentCheckError) {
        "there is an error getting the enrolment status" in {
          inSequence {
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck()(Left(""))
          }
          status(getResult()) shouldBe INTERNAL_SERVER_ERROR
        }
      }

      "there is an error getting the session data" in {
        inSequence {
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Left(""))
        }

        status(getResult()) shouldBe INTERNAL_SERVER_ERROR
      }

    }
  }

}
