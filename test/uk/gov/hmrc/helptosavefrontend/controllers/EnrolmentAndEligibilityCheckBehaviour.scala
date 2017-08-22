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
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.connectors.SessionCacheConnector
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithConfidence
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HTSSession, HtsAuth}
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.{NINO, UserDetailsURI}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait EnrolmentAndEligibilityCheckBehaviour {
  this: TestSupport ⇒

  val mockSessionCacheConnector: SessionCacheConnector = mock[SessionCacheConnector]

  val mockHelpToSaveService = mock[HelpToSaveService]

  val mockAuthConnector = mock[PlayAuthConnector]

  val nino = "NINO"
  val userDetailsURI = "user-details-uri"
  val enrolment = Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", nino)), "activated", ConfidenceLevel.L200)
  val enrolments = Enrolments(Set(enrolment))
  val userDetailsURIWithEnrolments = core.~[Option[UserDetailsURI], Enrolments](Some(userDetailsURI), enrolments)


  def mockSessionCacheConnectorPut(session: HTSSession)(result: Either[String, CacheMap]): Unit =
    (mockSessionCacheConnector.put(_: HTSSession)(_: Writes[HTSSession], _: HeaderCarrier, _: ExecutionContext))
      .expects(session, *, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockSessionCacheConnectorGet(result: Either[String, Option[HTSSession]]): Unit =
    (mockSessionCacheConnector.get(_: Reads[HTSSession], _: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEnrolmentCheck(input: NINO)(result: Either[String, EnrolmentStatus]): Unit =
    (mockHelpToSaveService.getUserEnrolmentStatus(_: NINO)(_: HeaderCarrier))
      .expects(input, *)
      .returning(EitherT.fromEither[Future](result))

  def mockWriteITMPFlag(nino: NINO)(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.setITMPFlag(_: NINO)(_: HeaderCarrier))
      .expects(nino, *)
      .returning(EitherT.fromEither[Future](result))


  def mockPlayAuthWithRetrievals[A, B](predicate: Predicate)(result: Option[UserDetailsURI] ~ Enrolments): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Option[UserDetailsURI] ~ Enrolments])(_: HeaderCarrier))
      .expects(predicate, HtsAuth.UserDetailsUrlWithAllEnrolments, *)
      .returning(Future.successful(result))

  def commonEnrolmentAndSessionBehaviour(getResult: () ⇒ Future[Result], // scalastyle:ignore method.length
                                         testRedirectOnNoSession: Boolean = true,
                                         testEnrolmentCheckError: Boolean = true): Unit = {

    "redirect to NS&I if the user is already enrolled" in {
      inSequence {
        mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
        mockEnrolmentCheck(nino)(Right(EnrolmentStatus.Enrolled(itmpHtSFlag = true)))
      }

      status(getResult()) shouldBe OK
    }

    "redirect to NS&I if the user is already enrolled and set the ITMP flag " +
      "if it has not already been set" in {
      inSequence {
        mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
        mockEnrolmentCheck(nino)(Right(EnrolmentStatus.Enrolled(itmpHtSFlag = false)))
        mockWriteITMPFlag(nino)(Right(()))
      }

      status(getResult()) shouldBe OK
    }

    "redirect to NS&I if the user is already enrolled even if there is an " +
      "setting the ITMP flag" in {
      inSequence {
        mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
        mockEnrolmentCheck(nino)(Right(EnrolmentStatus.Enrolled(itmpHtSFlag = false)))
        mockWriteITMPFlag(nino)(Left(""))
      }

      status(getResult()) shouldBe OK
    }


    if (testRedirectOnNoSession) {
      "redirect to the eligibility checks if there is no session data for the user" in {
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
          mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
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
            mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
            mockEnrolmentCheck(nino)(Left(""))
          }

          status(getResult()) shouldBe INTERNAL_SERVER_ERROR
        }
      }

      "there is an error getting the session data" in {
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence)(userDetailsURIWithEnrolments)
          mockEnrolmentCheck(nino)(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Left(""))
        }

        status(getResult()) shouldBe INTERNAL_SERVER_ERROR
      }

    }
  }

}
