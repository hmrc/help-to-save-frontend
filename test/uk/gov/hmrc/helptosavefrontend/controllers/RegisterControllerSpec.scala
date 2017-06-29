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
import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jackson.JsonLoader
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsValue, Reads, Writes}
import play.api.mvc.{Result => PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.connectors._
import uk.gov.hmrc.helptosavefrontend.controllers.RegisterController.OAuthConfiguration
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.{AuthWithConfidence, UserDetailsUrlWithAllEnrolments}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class RegisterControllerSpec extends TestSupport {

  private val mockHtsService = mock[HelpToSaveService]

  val userDetailsURI = "/dummy/user/details/uri"
  val nino = "WM123456C"

  private val enrolment = Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", nino)), "activated", ConfidenceLevel.L200)
  private val enrolments = Enrolments(Set(enrolment))

  private val mockAuthConnector = mock[PlayAuthConnector]
  val mockSessionCacheConnector: SessionCacheConnector = mock[SessionCacheConnector]
  val testOAuthConfiguration = OAuthConfiguration(true, "url", "client-ID", "callback", List("scope1", "scope2"))

  val oauthAuthorisationCode = "authorisation-code"

  val register = new RegisterController(
    fakeApplication.injector.instanceOf[MessagesApi],
    mockHtsService,
    mockSessionCacheConnector)(
    fakeApplication, ec) {
    override val oauthConfig = testOAuthConfiguration
    override lazy val authConnector = mockAuthConnector
  }

  def mockEligibilityResult(nino: String, userDetailsURI: String, authorisationCode: String)(result: Either[String, Option[UserInfo]]): Unit =
    (mockHtsService.checkEligibility(_: String, _: String, _: String)(_: HeaderCarrier))
      .expects(nino, userDetailsURI, authorisationCode, *)
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

  def mockCreateAccount(nSIUserInfo: NSIUserInfo)(response: Either[SubmissionFailure, SubmissionSuccess] = Right(SubmissionSuccess())): Unit =
    (mockHtsService.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nSIUserInfo, *, *)
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
      def doConfirmDetailsRequest(): Future[PlayResult] = register.getAuthorisation(FakeRequest())

      def doConfirmDetailsCallbackRequest(authorisationCode: String): Future[PlayResult] =
        register.confirmDetails(Some(authorisationCode), None, None, None)(FakeRequest())


      "redirect to confirm-details with the NINO as the authorisation code if redirects to OAUTH are disabled" in {
        val register = new RegisterController(
          fakeApplication.injector.instanceOf[MessagesApi],
          mockHtsService,
          mockSessionCacheConnector)(
          fakeApplication, ec) {
          override val oauthConfig = testOAuthConfiguration.copy(enabled = false)
          override lazy val authConnector = mockAuthConnector
        }

        mockPlayAuthWithRetrievals(AuthWithConfidence, UserDetailsUrlWithAllEnrolments)(uk.gov.hmrc.auth.core.~(Some(userDetailsURI), enrolments))

        implicit val request = FakeRequest()
        val result = register.getAuthorisation(request)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.confirmDetails(Some(nino), None,None,None).absoluteURL())
      }

      "return an if redirects to OAUTH are disabled and a NINO is not available" in {
        val register = new RegisterController(
          fakeApplication.injector.instanceOf[MessagesApi],
          mockHtsService,
          mockSessionCacheConnector)(
          fakeApplication, ec) {
          override val oauthConfig = testOAuthConfiguration.copy(enabled = false)
          override lazy val authConnector = mockAuthConnector
        }

        mockPlayAuthWithRetrievals(AuthWithConfidence, UserDetailsUrlWithAllEnrolments)(
          uk.gov.hmrc.auth.core.~(Some(userDetailsURI), Enrolments(Set.empty[Enrolment])))

        implicit val request = FakeRequest()
        val result = register.getAuthorisation(request)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.notEligible().absoluteURL())
      }

      "redirect to OAuth to get an access token if enabled" in {
        mockPlayAuthWithRetrievals(AuthWithConfidence, UserDetailsUrlWithAllEnrolments)(uk.gov.hmrc.auth.core.~(Some(userDetailsURI), enrolments))

        val result = doConfirmDetailsRequest()
        status(result) shouldBe Status.SEE_OTHER

        val (url, params) = redirectLocation(result).get.split('?').toList match {
          case u :: p :: Nil ⇒
            val paramList = p.split('&').toList
            val keyValueSet = paramList.map(_.split('=').toList match {
              case key:: value :: Nil ⇒ key → value
              case  _                 ⇒ fail(s"Could not parse query parameters: $p")
            }).toSet

            u → keyValueSet

          case _ ⇒ fail("Could not parse URL with query parameters")
        }

        url shouldBe testOAuthConfiguration.url
        params shouldBe (testOAuthConfiguration.scopes.map("scope" → _).toSet ++ Set(
          "client_id" -> testOAuthConfiguration.clientID,
          "response_type" -> "code",
          "redirect_uri" -> testOAuthConfiguration.callbackURL
        ))
      }

      "return a 500 if there is an error while getting the authorisation token" in {
        val result = register.confirmDetails(None, Some("uh oh"), None, None)(FakeRequest())
        status(result) shouldBe 500
      }

      "return user details if the user is eligible for help-to-save" in {
        val user = validUserInfo
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence, UserDetailsUrlWithAllEnrolments)(uk.gov.hmrc.auth.core.~(Some(userDetailsURI), enrolments))
          mockEligibilityResult(nino, userDetailsURI, oauthAuthorisationCode)(Right(Some(user)))
          mockSessionCacheConnectorPut(Right(CacheMap("1", Map.empty[String, JsValue])))
        }

        val responseFuture: Future[PlayResult] = doConfirmDetailsCallbackRequest(oauthAuthorisationCode)
        val result = Await.result(responseFuture, 5.seconds)

        status(result) shouldBe Status.OK

        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")

        val html = contentAsString(result)

        html should include(user.forename)
        html should include(user.email)
        html should include(user.nino)
      }

      "display a 'Not Eligible' page if the user is not eligible" in {
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence, UserDetailsUrlWithAllEnrolments)(uk.gov.hmrc.auth.core.~(Some("/dummy/user/details/uri"), enrolments))
          mockEligibilityResult(nino, userDetailsURI, oauthAuthorisationCode)(Right(None))
        }

        val result = doConfirmDetailsCallbackRequest(oauthAuthorisationCode)

        status(result) shouldBe Status.SEE_OTHER

        redirectLocation(result) shouldBe Some("/help-to-save/register/not-eligible")
      }

      "use the validate function to check empty JSON against empty schemas" in {
        val json = "{}"
        val schema = "{}"

        register.validateJsonAgainstSchema(json, schema) shouldBe None
      }

      "use the validate function to check arbitrary JSON against arbitrary schemas (example 1)" in {
        val json = """{"name": "Garfield", "food": "Lasagne"}"""
        val schema = """{"type": "object", "properties": {"name": {"type": "string"}, "food": {"type": "string"}}}"""

        register.validateJsonAgainstSchema(json, schema) shouldBe None
      }

      "use the validate function to check arbitrary JSON against arbitrary schemas (example 2)" in {
        val json = """{"name": 2, "food": 3}"""
        val schema = """{"type": "object", "properties": {"name": {"type": "string"}, "food": {"type": "string"}}}"""

        register.validateJsonAgainstSchema(json, schema).isDefined shouldBe true
      }

      "the validation schema value s a schema as defined by json-schema.org" in {
        val schemaJsonNode: JsonNode = JsonLoader.fromString(register.validationSchema)

        schemaJsonNode.isObject shouldBe true
      }


      "return an error" must {

        def isError(result: Future[PlayResult]): Boolean =
          status(result) == 500

        // test if the given mock actions result in an error when `confirm_details` is called
        // on the controller
        def test(mockActions: ⇒ Unit): Unit = {
          mockActions
          val result = doConfirmDetailsCallbackRequest(oauthAuthorisationCode)
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
              mockEligibilityResult(nino, userDetailsURI, oauthAuthorisationCode)(Left("Oh no"))
            })
        }

        "if the user details fo not pass NS&I validation checks" in {
          val user = validUserInfo.copy(forename = " space-at-beginning")
          test(inSequence {
            mockPlayAuthWithRetrievals(AuthWithConfidence, UserDetailsUrlWithAllEnrolments)(uk.gov.hmrc.auth.core.~(Some(userDetailsURI), enrolments))
            mockEligibilityResult(nino, userDetailsURI, oauthAuthorisationCode)(Right(Some(user)))
          })
        }

        "there is an error writing to the session cache" in {
          test(inSequence {
            mockPlayAuthWithRetrievals(AuthWithConfidence, UserDetailsUrlWithAllEnrolments)(uk.gov.hmrc.auth.core.~(Some(userDetailsURI), enrolments))
            mockEligibilityResult(nino, userDetailsURI, oauthAuthorisationCode)(Right(Some(validUserInfo)))
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

      "retrieve the user info from session cache and post it using " +
        "the help to save service" in {
        inSequence {
          mockPlayAuthWithWithConfidence()
          mockSessionCacheConnectorGet(Some(HTSSession(Some(validNSIUserInfo))))
          mockCreateAccount(validNSIUserInfo)()
        }
        val result = Await.result(doCreateAccountRequest(), 5.seconds)
        status(result) shouldBe Status.OK
      }


      "indicate to the user that the creation was successful if the creation was successful" in {
        inSequence {
          mockPlayAuthWithWithConfidence()
          mockSessionCacheConnectorGet(Some(HTSSession(Some(validNSIUserInfo))))
          mockCreateAccount(validNSIUserInfo)()
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
            mockSessionCacheConnectorGet(Some(HTSSession(Some(validNSIUserInfo))))
            mockCreateAccount(validNSIUserInfo)(Left(SubmissionFailure(None, "Uh oh", "Uh oh")))
          }

          val result = doCreateAccountRequest()
          val html = contentAsString(result)
          html should include("Account creation failed")
        }
      }
    }

  }

}
