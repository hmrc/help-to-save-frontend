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

import java.net.URLDecoder

import play.api.http.Status
import play.api.i18n.{Messages, MessagesApi}
import play.api.inject.Injector
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAuthConnector, WSHttp}
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validUserInfo
import uk.gov.hmrc.helptosavefrontend.models.VerifyEmailError.{AlreadyVerified, BackendError, RequestNotValidError, VerificationServiceUnavailable}
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HTSSession, SuspiciousActivity, VerifyEmailError}
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, EmailVerificationParams, NINO}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class NewApplicantUpdateEmailAddressControllerSpec extends AuthSupport with EnrolmentAndEligibilityCheckBehaviour {

  lazy val injector: Injector = fakeApplication.injector
  lazy val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  implicit val crypto: Crypto = fakeApplication.injector.instanceOf[Crypto]

  def messagesApi: MessagesApi = injector.instanceOf[MessagesApi]

  lazy val messages: Messages = messagesApi.preferred(request)

  val frontendAuthConnector: FrontendAuthConnector = stub[FrontendAuthConnector]

  val mockEmailVerificationConnector: EmailVerificationConnector = mock[EmailVerificationConnector]

  val mockHttp: WSHttp = mock[WSHttp]

  val mockAuditor = mock[HTSAuditor]

  lazy val controller: NewApplicantUpdateEmailAddressController =
    new NewApplicantUpdateEmailAddressController(
      mockSessionCacheConnector,
      mockHelpToSaveService,
      frontendAuthConnector,
      mockEmailVerificationConnector,
      mockMetrics,
      mockAuditor
    )(fakeApplication, fakeApplication.injector.instanceOf[MessagesApi], crypto, ec) {

      override val authConnector = mockAuthConnector
    }

  def mockEmailVerificationConn(nino: String, email: String)(result: Either[VerifyEmailError, Unit]) = {
    (mockEmailVerificationConnector.verifyEmail(_: String, _: String, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, email, true, *, *)
      .returning(Future.successful(result))
  }

  def mockAudit() =
    (mockAuditor.sendEvent(_: SuspiciousActivity, _: NINO))
      .expects(*, *)
      .returning(Future.successful(AuditResult.Success))

  "The NewApplicantUpdateEmailAddressController" when {

    "verifyEmail" must {

      val email = "email@gmail.com"

      "redirect to 'You're not Eligible' if the session data indicates they are ineligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
        }
        val result = await(controller.verifyEmail(email)(FakeRequest()))
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
      }

      "redirect to the eligibility check if there is no session data" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(None))
        }
        val result = await(controller.verifyEmail(email)(FakeRequest()))
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }

      "return the check your email page with a status of Ok" in {
        val newEmail = "e"
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validUserInfo), None))))
          mockEmailVerificationConn(nino, newEmail)(Right(()))
        }
        val result = await(controller.verifyEmail(newEmail)(FakeRequest()))
        status(result) shouldBe Status.OK
        contentAsString(result).contains(messagesApi("hts.email-verification.check-your-email.title")) shouldBe true
        contentAsString(result).contains(messagesApi("hts.email-verification.check-your-email.content")) shouldBe true
      }

      "return an AlreadyVerified status and redirect the user to email verified page," +
        " given an email address of an already verified user " in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validUserInfo), None))))
            mockEmailVerificationConn(nino, email)(Left(AlreadyVerified))
          }
          val result = await(controller.verifyEmail(email)(FakeRequest()))
          status(result) shouldBe Status.SEE_OTHER

          val redirectURL = redirectLocation(result)

          redirectURL
            .getOrElse(fail("Could not find redirect location"))
            .split('=')
            .toList match {
              case _ :: param :: Nil ⇒
                EmailVerificationParams.decode(URLDecoder.decode(param, "UTF-8")) match {
                  case Success(params) ⇒
                    params.nino shouldBe nino
                    params.email shouldBe email

                  case Failure(e) ⇒ fail(s"Could not decode email verification parameters string: $param", e)
                }

              case _ ⇒ fail(s"Unexpected redirect location found: $redirectURL")
            }
        }

      "show an email verificatino error page if the email verification is unsuccessful" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validUserInfo), None))))
          mockEmailVerificationConn(nino, email)(Left(BackendError))
        }

        val result = await(controller.verifyEmail(email)(FakeRequest()))
        status(result) shouldBe Status.OK
        contentAsString(result) should include("verification error")
      }

    }

    "emailVerified" should {
      val testEmail = "email@gmail.com"

        def doRequestWithQueryParam(p: String): Future[Result] = controller.emailVerified(p)(FakeRequest())

      "show the check and confirm your details page showing the users details with the verified user email address " +
        "if the user has not already enrolled and " +
        "the session data shows that they have been already found to be eligible " +
        "and the user has clicked on the verify email link sent to them by the email verification service and the nino from auth " +
        "matches that passed in via the params" in {

          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validUserInfo), None))))
            mockSessionCacheConnectorPut(HTSSession(Some(validUserInfo.updateEmail(testEmail)), Some(testEmail)))(Right(()))
          }

          val params = EmailVerificationParams(validUserInfo.nino, testEmail)
          val result = doRequestWithQueryParam(params.encode())
          status(result) shouldBe Status.OK
          contentAsString(result) should include("Your email has been updated")
        }

      "return an OK status when the link has been corrupted or is incorrect" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockAudit()
        }
        val result = doRequestWithQueryParam("corrupt-link")
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Email verification error")
      }

      "return an OK status with a not eligible view when an ineligible user comes in via email verified" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
        }
        val params = EmailVerificationParams(validUserInfo.nino, testEmail)
        val result = doRequestWithQueryParam(params.encode())
        status(result) shouldBe Status.OK
        contentAsString(result) should include("not eligible for Help to Save")
      }

      "return an Internal Server Error" when {

          def test(mockActions: ⇒ Unit, nino: String, email: String) = {
            mockActions
            val params = EmailVerificationParams(nino, email)
            val result = doRequestWithQueryParam(params.encode())
            checkIsTechnicalErrorPage(result)
          }

        "the user has not already enrolled and the given nino doesn't match the session nino" in {
          test(inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validUserInfo), None))))
          },
            "AE1234XXX",
               testEmail
          )
        }

        "the sessionCacheConnector.get method returns an error" in {
          test(inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionCacheConnectorGet(Left("An error occurred"))
          },
               validUserInfo.nino,
               testEmail
          )
        }

        "the sessionCacheConnector.put method returns an error" in {
          test(inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validUserInfo), None))))
            mockSessionCacheConnectorPut(HTSSession(Some(validUserInfo.updateEmail(testEmail)), Some(testEmail)))(Left("An error occurred"))
          },
               validUserInfo.nino,
               testEmail
          )
        }

        "the user has missing info and they do not have a session" in {
          test(inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
            mockSessionCacheConnectorGet(Right(None))
          },
               validUserInfo.nino,
               testEmail
          )

        }
      }

    }

    "handling getEmailUpdated" must {

        def doRequest(): Future[Result] =
          controller.getEmailUpdated()(FakeRequest())

      "redirect to NS&I if they are already enrolled to HtS" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.NSIController.goToNSI().url)
      }

      "redirect to the eligibility checks is there is no session data" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(None))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
      }

      "redirect to the not eligible page if the session data indicates they are ineligible" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None))))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
      }

      "show the email updated page otherwise" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(validUserInfo), None))))
        }

        val result = doRequest()
        status(result) shouldBe OK
        contentAsString(result) should include("email has been updated")

      }

    }
  }
}
