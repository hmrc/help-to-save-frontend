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
import cats.instances.string._
import cats.syntax.either._
import cats.syntax.eq._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Result ⇒ PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.{Token, TokenProvider}
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAppConfig, FrontendAuthConnector}
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthProvider
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility.randomIneligibility
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.{validNSIUserInfo, validUserInfo}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.services.JSONSchemaValidationService
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, NINO}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class RegisterControllerSpec extends AuthSupport with EnrolmentAndEligibilityCheckBehaviour with GeneratorDrivenPropertyChecks {

  val jsonSchemaValidationService: JSONSchemaValidationService = mock[JSONSchemaValidationService]
  val mockAuditor: HTSAuditor = mock[HTSAuditor]
  val frontendAuthConnector: FrontendAuthConnector = stub[FrontendAuthConnector]
  implicit val crypto: Crypto = fakeApplication.injector.instanceOf[Crypto]

  val controller: RegisterController = new RegisterController(
    fakeApplication.injector.instanceOf[MessagesApi],
    mockHelpToSaveService,
    mockSessionCacheConnector,
    frontendAuthConnector,
    jsonSchemaValidationService,
    mockMetrics,
    mockAuditor,
    fakeApplication)(
    ec, crypto) {
    override lazy val authConnector = mockAuthConnector
  }

  def mockCreateAccount(nSIUserInfo: NSIUserInfo)(response: Either[SubmissionFailure, SubmissionSuccess] = Right(SubmissionSuccess())): Unit =
    (mockHelpToSaveService.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nSIUserInfo, *, *)
      .returning(EitherT.fromEither[Future](response))

  def mockEnrolUser()(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.enrolUser()(_: HeaderCarrier))
      .expects(*)
      .returning(EitherT.fromEither[Future](result))

  def mockEmailUpdate(email: String)(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.storeConfirmedEmail(_: String)(_: HeaderCarrier))
      .expects(email, *)
      .returning(EitherT.fromEither[Future](result))

  def mockAccountCreationAllowed(result: Either[String, Boolean]): Unit =
    (mockHelpToSaveService.isAccountCreationAllowed()(_: HeaderCarrier))
      .expects(*)
      .returning(EitherT.fromEither[Future](result))

  def mockUpdateUserCount(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.updateUserCount()(_: HeaderCarrier))
      .expects(*)
      .returning(EitherT.fromEither[Future](result))

  def mockDecrypt(expected: String)(result: Option[String]) =
    (crypto.decrypt(_: String))
      .expects(expected)
      .returning(result.fold[Try[String]](Failure(new Exception))(Success.apply))

  def mockAudit() =
    (mockAuditor.sendEvent(_: AccountCreated, _: NINO))
      .expects(*, nino)
      .returning(Future.successful(AuditResult.Success))

  def mockJsonSchemaValidation(input: NSIUserInfo)(result: Either[String, Unit]): Unit =
    (jsonSchemaValidationService.validate(_: JsValue))
      .expects(Json.toJson(input))
      .returning(result.map(_ ⇒ Json.toJson(input)))

  lazy val tokenProvider: TokenProvider =
    fakeApplication.injector.instanceOf[TokenProvider]

  val fakeRequest = FakeRequest().copyFakeRequest(tags = Map(
    Token.NameRequestTag → "csrfToken",
    Token.RequestTag → tokenProvider.generateToken))

  def checkRedirectIfNoEmailInSession(doRequest: ⇒ Future[PlayResult]): Unit = {
    "redirect to the give email page if the session data does not contain an email for the user" in {
      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo.copy(email = None)), None))))
      }

      val result = doRequest
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.RegisterController.getGiveEmailPage().url)
    }
  }

  def checkRedirectIfEmailInSession(doRequest: ⇒ Future[PlayResult]): Unit = {
    "redirect to the confirm email page if the session data contains an email for the user" in {
      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
      }

      val result = doRequest
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.RegisterController.getSelectEmailPage().url)
    }
  }

  "The RegisterController" when {

    "handling getGiveEmailPage" must {

        def doRequest(): Future[PlayResult] = controller.getGiveEmailPage(fakeRequest)

      checkRedirectIfEmailInSession(doRequest())

      "indicate to the user that user-cap has already reached and account creation not possible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo.copy(email = None)), None))))
          mockAccountCreationAllowed(Right(false))
        }

        val result = doRequest()
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getUserCapReachedPage().url)
      }

      "return the give email page" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo.copy(email = None)), None))))
          mockAccountCreationAllowed(Right(true))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include("we don't have an email for you")
      }

    }

    "handling giveEmailSubmit" must {

      val email = "email@test.com"

        def doRequest(email: String): Future[PlayResult] = controller.giveEmailSubmit()(
          fakeRequest.withFormUrlEncodedBody("email" → email))

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest(email))

      checkRedirectIfEmailInSession(doRequest(email))

      "return to the give email page if the form does not contain a valid email" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo.copy(email = None)), None))))
        }

        val result = doRequest("this is not an email")
        status(result) shouldBe Status.OK
        contentAsString(result) should include("we don't have an email for you")
      }

      "redirect to verify email if the form does contains a valid email" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo.copy(email = None)), None))))
        }

        val result = doRequest(email)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.NewApplicantUpdateEmailAddressController.verifyEmail(email).url)
      }

    }

    "handling getSelectEmailPage" must {

        def doRequest(): Future[PlayResult] = controller.getSelectEmailPage(fakeRequest)

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest())

      checkRedirectIfNoEmailInSession(doRequest())

      "indicate to the user that user-cap has already reached and account creation not possible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
          mockAccountCreationAllowed(Right(false))
        }

        val result = doRequest()
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getUserCapReachedPage().url)
      }

      "return the confirm email page" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
          mockAccountCreationAllowed(Right(true))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Which email address do you want us to use for your Help to Save account?")
      }
    }

    "handling getSElectEmailSubmit" must {

        def doRequest(newEmail: Option[String]): Future[PlayResult] = {
          newEmail.fold(
            controller.selectEmailSubmit()(fakeRequest.withFormUrlEncodedBody("email" → "Yes"))
          ) { e ⇒
              controller.selectEmailSubmit()(fakeRequest.withFormUrlEncodedBody("email" → "No", "new-email" → e))
            }

        }

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest(None))

      checkRedirectIfNoEmailInSession(doRequest(None))

      "write the email to keystore and the email store if the user has not already enrolled and " +
        "the session data shows that they have been already found to be eligible and the form contains no new email" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
            mockSessionCacheConnectorPut(HTSSession(Right(validUserInfo), Some(emailStr)))(Right(CacheMap("", Map.empty)))
            mockEmailUpdate(emailStr)(Left(""))
          }

          await(doRequest(None))
        }

      "redirect to the create an account page if the write to keystore and the email store was successful" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
          mockSessionCacheConnectorPut(HTSSession(Right(validUserInfo), Some(emailStr)))(Right(CacheMap("", Map.empty)))
          mockEmailUpdate(emailStr)(Right(()))
        }

        val result = doRequest(None)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountHelpToSavePage().url)
      }

      "redirect to verify email if the session data shows that they have been already found to be eligible " +
        "and the form contains a valid new email" in {
          val newEmail = "email@test.com"

          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
          }

          val result = doRequest(Some(newEmail))
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.NewApplicantUpdateEmailAddressController.verifyEmail(newEmail).url)
        }

      "redirect to the confirm email page if the session data shows that they have been already found to be eligible and " when {

        "the form contains a invalid new email" in {
          val invalidEmail = "not-an-email"

          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
          }

          val result = doRequest(Some(invalidEmail))
          status(result) shouldBe Status.OK
          contentAsString(result) should include("Which email")
        }

        "no option has been selected" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
          }

          val result = controller.selectEmailSubmit()(fakeRequest.withFormUrlEncodedBody("new-email" → "email@test.com"))

          status(result) shouldBe Status.OK
          contentAsString(result) should include("Which email")
        }

        "a new email has been selected but there is no new email given" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
          }

          val result = controller.selectEmailSubmit()(fakeRequest.withFormUrlEncodedBody("email" → "No"))

          status(result) shouldBe Status.OK
          contentAsString(result) should include("Which email")
        }

        "the 'email' key of the form is not 'Yes' or 'No'" in {
          forAll { s: String ⇒
            whenever(s =!= "Yes" | s =!= "No") {
              inSequence {
                mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
                mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
                mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
              }

              val result = controller.selectEmailSubmit()(fakeRequest.withFormUrlEncodedBody("email" → s))

              status(result) shouldBe Status.OK
              contentAsString(result) should include("Which email")

            }

          }
        }

      }

      "return an error" when {
        "the email cannot be written to keystore" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
            mockSessionCacheConnectorPut(HTSSession(Right(validUserInfo), Some(emailStr)))(Left(""))
          }

          val result = doRequest(None)
          checkIsTechnicalErrorPage(result)
        }

        "the email cannot be written to the email store" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
            mockSessionCacheConnectorPut(HTSSession(Right(validUserInfo), Some(emailStr)))(Right(CacheMap("", Map.empty)))
            mockEmailUpdate(emailStr)(Left(""))
          }

          val result = doRequest(None)
          checkIsTechnicalErrorPage(result)
        }
      }
    }

    "handling getUserCapReachedPage" must {

      "return the user cap reached page" in {
        mockAuthWithNoRetrievals(AuthProvider)

        val result = controller.getUserCapReachedPage(FakeRequest())
        status(result) shouldBe Status.OK
        contentAsString(result) should include("User Limit Reached")
      }

    }

    "handling getDetailsAreIncorrect" must {

      "return the details are incorrect page" in {
        mockAuthWithNoRetrievals(AuthProvider)

        val result = controller.getDetailsAreIncorrect(FakeRequest())
        status(result) shouldBe Status.OK
        contentAsString(result) should include("nothing you can do")
      }
    }

    "handling a getCreateAccountHelpToSave" must {

      val email = "email"

        def doRequest(): Future[PlayResult] =
          controller.getCreateAccountHelpToSavePage()(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(() ⇒ doRequest())

      checkRedirectIfNoEmailInSession(doRequest())

      "redirect the user to the confirm details page if there is no email in the session data" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getSelectEmailPage().url)
      }

      "show the user the create account page if the session data contains a confirmed email" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), Some(email)))))
        }

        val result = doRequest()
        status(result) shouldBe OK
        contentAsString(result) should include("Accept and create account")
      }

    }

    "creating an account" must {
      val confirmedEmail = "confirmed"

        def doCreateAccountRequest(): Future[PlayResult] = controller.createAccountHelpToSave(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(doCreateAccountRequest)

      checkRedirectIfNoEmailInSession(doCreateAccountRequest())

      "retrieve the user info from session cache and indicate to the user that the creation was successful " +
        "and enrol the user if the creation was successful" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), Some(confirmedEmail)))))
            mockJsonSchemaValidation(validNSIUserInfo.updateEmail(confirmedEmail))(Right(()))
            mockCreateAccount(validNSIUserInfo.updateEmail(confirmedEmail))()
            mockAudit()
            mockUpdateUserCount(Right(Unit))
            mockEnrolUser()(Right(()))
          }

          val result = doCreateAccountRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(FrontendAppConfig.nsiManageAccountUrl)
        }

      "indicate to the user that the creation was successful " +
        "and even if the user couldn't be enrolled" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), Some(confirmedEmail)))))
            mockJsonSchemaValidation(validNSIUserInfo.updateEmail(confirmedEmail))(Right(()))
            mockCreateAccount(validNSIUserInfo.updateEmail(confirmedEmail))()
            mockAudit()
            mockUpdateUserCount(Right(Unit))
            mockEnrolUser()(Left("Oh no"))
          }

          val result = doCreateAccountRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(FrontendAppConfig.nsiManageAccountUrl)
        }

      "redirect the user to the confirm details page if the session indicates they have not done so already" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), None))))
        }

        val result = doCreateAccountRequest()
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getSelectEmailPage().url)
      }

      "indicate to the user that the creation was not successful " when {

        "the JSON schema validation fails" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), Some(confirmedEmail)))))
            mockJsonSchemaValidation(validNSIUserInfo.updateEmail(confirmedEmail))(Left(""))
          }

          val result = doCreateAccountRequest()
          checkIsTechnicalErrorPage(result)
        }

        "the help to save service returns with an error" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Right(validUserInfo), Some(confirmedEmail)))))
            mockJsonSchemaValidation(validNSIUserInfo.updateEmail(confirmedEmail))(Right(()))
            mockCreateAccount(validNSIUserInfo.updateEmail(confirmedEmail))(Left(SubmissionFailure(None, "Uh oh", "Uh oh")))
          }

          val result = doCreateAccountRequest()
          checkIsTechnicalErrorPage(result)
        }
      }
    }
  }
}
