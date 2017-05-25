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
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsValue, Reads, Writes}
import play.api.mvc.{Result => PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.connectors._
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.{AuthWithConfidence, UserDetailsUrlWithAllEnrolments}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class RegisterControllerSpec extends TestSupport with ScalaFutures {

  private val mockHtsService = mock[HelpToSaveService]

  val userDetailsURI = "/dummy/user/details/uri"
  val nino = "WM123456C"

  private val enrolment = Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", nino)), "activated", ConfidenceLevel.L200)
  private val enrolments = Enrolments(Set(enrolment))

  private val mockAuthConnector = mock[PlayAuthConnector]
  val mockSessionCacheConnector: SessionCacheConnector = mock[SessionCacheConnector]

  val register = new RegisterController(
    fakeApplication.injector.instanceOf[MessagesApi],
    mockHtsService,
    mockSessionCacheConnector)(
    fakeApplication) {
    override lazy val authConnector = mockAuthConnector
  }


  def mockEligibilityResult(nino: String, userDetailsURI: String)(result: Either[String, Option[UserInfo]]): Unit =
    (mockHtsService.checkEligibility(_: String, _: String)(_: HeaderCarrier))
      .expects(nino, userDetailsURI, *)
      .returning(EitherT.fromEither[Future](result.map(EligibilityResult(_))))

  def mockSessionCacheConnectorPut(result: Either[String, CacheMap]): Unit =
    (mockSessionCacheConnector.put(_: HTSSession)(_: Writes[HTSSession], _: HeaderCarrier))
      .expects(*, *, *)
      .returning(result.fold(
        e ⇒ Future.failed(new Exception(e)),
        Future.successful))

  def mockSessionCacheConnectorGet(mockHtsSession: Option[HTSSession]): Unit =
    (mockSessionCacheConnector.get(_: HeaderCarrier, _: Reads[HTSSession]))
      .expects(*, *)
      .returning(Future.successful(mockHtsSession))

  def mockCreateAccount(response: Either[String, Unit] = Right(())): Unit =
    (mockHtsService.createAccount(_: UserInfo)(_: HeaderCarrier))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](response))

  def mockPlayAuthWithRetrievals[A, B](predicate: Predicate, retrieval: Retrieval[A ~ B])(result: A ~ B): Unit =
    (mockAuthConnector.authorise[A ~ B](_: Predicate, _: Retrieval[uk.gov.hmrc.auth.core.~[A, B]])(_: HeaderCarrier))
      .expects(predicate, retrieval, *)
      .returning(Future.successful(result))

  def mockPlayAuthWithWithConfidence(): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier))
      .expects(AuthWithConfidence, *, *)
      .returning(Future.successful(()))


  "The RegisterController" when {

    "checking eligibility" must {

      def doUserDetailsRequest(): Future[PlayResult] = register.userDetails(FakeRequest())

      "return user details if the user is eligible for help-to-save" in {
        val user = randomUserInfo()
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence, UserDetailsUrlWithAllEnrolments)(uk.gov.hmrc.auth.core.~(Some(userDetailsURI), enrolments))
          mockEligibilityResult(nino, userDetailsURI)(Right(Some(user)))
          mockSessionCacheConnectorPut(Right(CacheMap("1", Map.empty[String, JsValue])))
        }

        val responseFuture: Future[PlayResult] = doUserDetailsRequest()
        val result = responseFuture.futureValue

        status(result) shouldBe Status.OK

        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")

        val html = contentAsString(result)

        html should include(user.forename)
        html should include(user.email)
        html should include(user.NINO)
      }

      "display a 'Not Eligible' page if the user is not eligible" in {
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence, UserDetailsUrlWithAllEnrolments)(uk.gov.hmrc.auth.core.~(Some("/dummy/user/details/uri"), enrolments))
          mockEligibilityResult(nino, userDetailsURI)(Right(None))
        }

        val result = doUserDetailsRequest()
        val html = contentAsString(result)

        html should include("not eligible")
        html should include("To be eligible for an account")
      }


      "return an error" when {

        def isError(result: Future[PlayResult]): Boolean =
          status(result) == 500

        // test if the given mock actions result in an error when `userDetails` is called
        // on the controller
        def test(mockActions: ⇒ Unit): Unit = {
          mockActions
          val result = doUserDetailsRequest()
          isError(result) shouldBe true
        }

        "the nino is not available" in {
          test(
            mockPlayAuthWithRetrievals(AuthWithConfidence, UserDetailsUrlWithAllEnrolments)(
              uk.gov.hmrc.auth.core.~(Some(userDetailsURI), Enrolments(Set.empty))))
        }

        "the user details URI is not available" in {
          test(
            mockPlayAuthWithRetrievals(AuthWithConfidence, UserDetailsUrlWithAllEnrolments)(
              uk.gov.hmrc.auth.core.~(None, enrolments)))
        }

        "the eligibility check call returns with an error" in {
          test(
            inSequence {
              mockPlayAuthWithRetrievals(AuthWithConfidence, UserDetailsUrlWithAllEnrolments)(uk.gov.hmrc.auth.core.~(Some(userDetailsURI), enrolments))
              mockEligibilityResult(nino, userDetailsURI)(Left("Oh no"))
            })
        }

        "there is an error writing to the session cache" in {
          val user = randomUserInfo()
          test(inSequence {
            mockPlayAuthWithRetrievals(AuthWithConfidence, UserDetailsUrlWithAllEnrolments)(uk.gov.hmrc.auth.core.~(Some(userDetailsURI), enrolments))
            mockEligibilityResult(nino, userDetailsURI)(Right(Some(user)))
            mockSessionCacheConnectorPut(Left("Bang"))
          })
        }
      }
    }


    "handling a getCreateAccountHelpToSave" must {

      "return 200" in {
        mockPlayAuthWithWithConfidence()
        val result = register.getCreateAccountHelpToSavePage(FakeRequest())
        status(result) shouldBe Status.OK
        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")
      }
    }

    "creating an account" must {
      def doCreateAccountRequest(): Future[PlayResult] = register.createAccountHelpToSave(FakeRequest())

      val user = randomUserInfo()

      "retrieve the user info from session cache and post it using " +
        "the help to save service" in {
        inSequence {
          mockPlayAuthWithWithConfidence()
          mockSessionCacheConnectorGet(Some(HTSSession(Some(user))))
          mockCreateAccount()
        }

        doCreateAccountRequest().futureValue
      }


      "indicate to the user that the creation was successful if the creation was successful" in {
        inSequence {
          mockPlayAuthWithWithConfidence()
          mockSessionCacheConnectorGet(Some(HTSSession(Some(user))))
          mockCreateAccount()
        }

        val result = doCreateAccountRequest()
        val html = contentAsString(result)
        html should include("Successfully created account")
      }

      "indicate to the user that the creation was not successful " when {

        "the user details cannot be found in the session cache" in {
          inSequence {
            mockPlayAuthWithWithConfidence()
            mockSessionCacheConnectorGet(None)
          }

          val result = doCreateAccountRequest()
          val html = contentAsString(result)
          html should include("Account creation failed")
        }

        "the help to save service returns with an error" in {
          inSequence {
            mockPlayAuthWithWithConfidence()
            mockSessionCacheConnectorGet(Some(HTSSession(Some(user))))
            mockCreateAccount(Left("Uh oh"))
          }

          val result = doCreateAccountRequest()
          val html = contentAsString(result)
          html should include("Account creation failed")
        }
      }
    }

  }

}
