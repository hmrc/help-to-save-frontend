/*
 * Copyright 2018 HM Revenue & Customs
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

import cats.data.EitherT
import cats.instances.future._
import play.api.http.Status
import play.api.i18n.Messages
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth._
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility._
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validUserInfo
import uk.gov.hmrc.helptosavefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResult.AlreadyHasAccount
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError.{AlreadyVerified, OtherError}
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HTSSession, SuspiciousActivity}
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, EmailVerificationParams, NINO}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class NewApplicantUpdateEmailAddressControllerSpec
  extends AuthSupport
  with CSRFSupport
  with EnrolmentAndEligibilityCheckBehaviour
  with SessionCacheBehaviour {

  lazy val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  lazy val messages: Messages = messagesApi.preferred(request)

  val mockEmailVerificationConnector: EmailVerificationConnector = mock[EmailVerificationConnector]

  val mockHttp: WSHttp = mock[WSHttp]

  val mockAuditor = mock[HTSAuditor]

  def newController()(implicit crypto: Crypto) =
    new NewApplicantUpdateEmailAddressController(
      mockSessionCacheConnector,
      mockHelpToSaveService,
      mockAuthConnector,
      mockEmailVerificationConnector,
      mockMetrics,
      mockAuditor
    ) {
      override val authConnector = mockAuthConnector
    }

  lazy val controller = newController()

  val eligibleWithValidUserInfo = randomEligibleWithUserInfo(validUserInfo)

  def mockEmailVerificationConn(nino: String, email: String, firstName: String)(result: Either[VerifyEmailError, Unit]) = {
    (mockEmailVerificationConnector.verifyEmail(_: String, _: String, _: String, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, email, firstName, true, *, *)
      .returning(Future.successful(result))
  }

  def mockAudit() =
    (mockAuditor.sendEvent(_: SuspiciousActivity, _: NINO)(_: ExecutionContext))
      .expects(*, *, *)
      .returning(Future.successful(AuditResult.Success))

  def mockStoreConfirmedEmail(email: String)(result: Either[String, Unit]): Unit =
    (mockHelpToSaveService.storeConfirmedEmail(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(email, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEligibilityResult()(result: Either[String, EligibilityCheckResult]): Unit =
    (mockHelpToSaveService.checkEligibility()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def mockGetUserEnrolmentStatus()(result: Either[String, EnrolmentStatus]): Unit =
    (mockHelpToSaveService.getUserEnrolmentStatus()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  "The NewApplicantUpdateEmailAddressController" when {

    "verifyEmail" must {

      val email = "email@gmail.com"

      behave like commonBehaviour(() ⇒ controller.verifyEmail(FakeRequest()))

      "return the check your email page with a status of Ok" in {
        val newEmail = "e"
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, Some(newEmail)))))
          mockEmailVerificationConn(nino, newEmail, firstName)(Right(()))
        }
        val result = await(controller.verifyEmail(fakeRequestWithCSRFToken))
        status(result) shouldBe Status.OK
        contentAsString(result).contains(messagesApi("hts.email-verification.check-your-email.title")) shouldBe true
        contentAsString(result).contains(messagesApi("hts.email-verification.check-your-email.content2")) shouldBe true

      }

      "return an AlreadyVerified status and redirect the user to email verified page," +
        " given an email address of an already verified user " in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, Some(email)))))
            mockEmailVerificationConn(nino, email, firstName)(Left(AlreadyVerified))
          }
          val result = await(controller.verifyEmail(FakeRequest()))(10.seconds)
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

      "redirect to the email verification error page if the email verification is unsuccessful " +
        "and an email exists for the user" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, Some(email)))))
            mockEmailVerificationConn(nino, email, firstName)(Left(OtherError))
          }

          val result = controller.verifyEmail(fakeRequestWithCSRFToken)
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.NewApplicantUpdateEmailAddressController.verifyEmailError().url)
        }

      "redirect to the email verification error page if the email verification is unsuccessful and " +
        "an email does not exist for the user" in {
          val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo.copy(email = None))
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo)), None, Some(email)))))
            mockEmailVerificationConn(nino, email, firstName)(Left(OtherError))
          }

          val result = controller.verifyEmail(FakeRequest())
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)
        }

      "show an error if there is no pending email in keystore" in {
        val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo.copy(email = None))
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))))
        }

        val result = controller.verifyEmail(FakeRequest())
        checkIsTechnicalErrorPage(result)
      }

    }

    "emailVerifyError" should {

        def doRequest(): Future[Result] = controller.verifyEmailError()(fakeRequestWithCSRFToken)

      behave like commonBehaviour(doRequest)

      "show the email verify error page if we hold an email for the user" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
        }

        val result = doRequest()
        status(result) shouldBe Status.OK
        contentAsString(result) should include(":(")
      }

      "redirect the email verify error try later page if there is no email for the user" in {
        val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo.copy(email = None))
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))))
        }

        val result = doRequest()
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)
      }

    }

    "emailVerifyErrorSubmit" should {

        def doRequest(continue: Boolean): Future[Result] =
          controller.verifyEmailErrorSubmit()(fakeRequestWithCSRFToken.withFormUrlEncodedBody("radio-inline-group" → continue.toString))

      behave like commonBehaviour(() ⇒ doRequest(true))

      "redirect to the email verify error page try later if there is no email for the user" in {
        val eligibleWithUserInfo = randomEligibleWithUserInfo(validUserInfo.copy(email = None))
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithUserInfo)), None, None))))
        }

        val result = doRequest(true)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)
      }

      "redirect to the confirmEmail endpoint if there is an email for the user and the user selects to continue" in {
        val crypto: Crypto = mock[Crypto]
        val controller = newController()(crypto)
        val encryptedEmail = "encrypted"

        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
          (crypto.encrypt(_: String)).expects(emailStr).returning(encryptedEmail)
        }

        val result = controller.verifyEmailErrorSubmit()(fakeRequestWithCSRFToken.withFormUrlEncodedBody("radio-inline-group" → "true"))
        status(result) shouldBe Status.SEE_OTHER

        redirectLocation(result) shouldBe Some(routes.RegisterController.confirmEmail(encryptedEmail).url)
      }

      "redirect to the info endpoint if there is an email for the user and the user selects not to continue" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
        }

        val result = doRequest(false)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.IntroductionController.getAboutHelpToSave().url)
      }

      "show the verify email error page again if there is an error in the form" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
        }

        val result = controller.verifyEmailErrorSubmit()(fakeRequestWithCSRFToken)
        status(result) shouldBe Status.OK
        contentAsString(result) should include("We cannot change your email address at the moment")
      }
    }

    "emailVerifiedCallback" should {
      val testEmail = "email@gmail.com"

      val eligibleWithUserInfoWithUpdatedEmail =
        eligibleWithValidUserInfo.copy(userInfo = eligibleWithValidUserInfo.userInfo.updateEmail(testEmail))

        def doRequestWithQueryParam(p: String): Future[Result] = controller.emailVerifiedCallback(p)(fakeRequestWithCSRFToken)

      "show the check and confirm your details page showing the users details with the verified user email address " when {

        "the user has not already enrolled and" when {

          "the session data shows that they have been already found to be eligible " +
            "the nino from auth matches that passed in via the params and they have session data" in {

              inSequence {
                mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
                mockGetUserEnrolmentStatus()(Right(NotEnrolled))
                mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, Some("pending")))))
                mockSessionCacheConnectorPut(HTSSession(Some(Right(eligibleWithUserInfoWithUpdatedEmail)), Some(testEmail), Some("pending")))(Right(()))
                mockStoreConfirmedEmail(testEmail)(Right(()))
              }

              val params = EmailVerificationParams(validUserInfo.nino, testEmail)
              val result = doRequestWithQueryParam(params.encode())
              status(result) shouldBe Status.SEE_OTHER
              redirectLocation(result) shouldBe Some(routes.NewApplicantUpdateEmailAddressController.getEmailVerified().url)
            }

          "the session data shows that they have been already found to be eligible " +
            "the nino from auth matches that passed in via the params and they don't have session data and an " +
            "eligiblity check indicates that they are eligible" in {
              inSequence {
                mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
                mockGetUserEnrolmentStatus()(Right(NotEnrolled))
                mockSessionCacheConnectorGet(Right(None))
                mockEligibilityResult()(Right(eligibleWithUserInfoWithUpdatedEmail.eligible))
                mockSessionCacheConnectorPut(HTSSession(Some(Right(eligibleWithUserInfoWithUpdatedEmail)), Some(testEmail), None))(Right(()))
                mockStoreConfirmedEmail(testEmail)(Right(()))
              }

              val params = EmailVerificationParams(validUserInfo.nino, testEmail)
              val result = doRequestWithQueryParam(params.encode())
              status(result) shouldBe Status.SEE_OTHER
              redirectLocation(result) shouldBe Some(routes.NewApplicantUpdateEmailAddressController.getEmailVerified().url)
            }

        }
      }

      "redirect to the how to access account page" when {
        "the user clicks on their email verification link but they already have an account" in {
          val alreadyHasAccountResult = AlreadyHasAccount(EligibilityCheckResponse("User already has an account", 3, "", 1))
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockGetUserEnrolmentStatus()(Right(Enrolled(true)))
          }
          val params = EmailVerificationParams(validUserInfo.nino, testEmail)
          val result = doRequestWithQueryParam(params.encode())
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.NewApplicantUpdateEmailAddressController.getLinkExpiredPage().url)
        }
      }

      "return an OK status when the link has been corrupted or is incorrect" in {
        inSequence {
          mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockGetUserEnrolmentStatus()(Right(NotEnrolled))
          mockAudit()
        }
        val result = doRequestWithQueryParam("corrupt-link")
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)
      }

      "show the not eligible page " when {
        "the session data indicates that they are ineligible" in {
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockGetUserEnrolmentStatus()(Right(NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Left(randomIneligibility())), None, None))))
          }
          val params = EmailVerificationParams(validUserInfo.nino, testEmail)
          val result = doRequestWithQueryParam(params.encode())
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
        }

        "there is no session data but an eligibility check indicates that they are ineligible" in {
          // TODO
          inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockGetUserEnrolmentStatus()(Right(NotEnrolled))
            mockSessionCacheConnectorGet(Right(None))
            mockEligibilityResult()(Right(randomIneligibility()))
          }
          val params = EmailVerificationParams(validUserInfo.nino, testEmail)
          val result = doRequestWithQueryParam(params.encode())
          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
        }
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
            mockGetUserEnrolmentStatus()(Right(NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
          },
            "AE1234XXX",
               testEmail
          )
        }

        "the sessionCacheConnector.get method returns an error" in {
          test(inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockGetUserEnrolmentStatus()(Right(NotEnrolled))
            mockSessionCacheConnectorGet(Left("An error occurred"))
          },
               validUserInfo.nino,
               testEmail
          )
        }

        "the sessionCacheConnector.put method returns an error" in {
          test(inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockGetUserEnrolmentStatus()(Right(NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
            mockSessionCacheConnectorPut(HTSSession(Some(Right(eligibleWithUserInfoWithUpdatedEmail)), Some(testEmail), None))(Left("An error occurred"))
          },
               validUserInfo.nino,
               testEmail
          )
        }

        "the confirmed email cannot be stored" in {
          test(inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockGetUserEnrolmentStatus()(Right(NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
            mockSessionCacheConnectorPut(HTSSession(Some(Right(eligibleWithUserInfoWithUpdatedEmail)), Some(testEmail), None))(Right(()))
            mockStoreConfirmedEmail(testEmail)(Left(""))
          }, validUserInfo.nino, testEmail)
        }

        "the user has missing info and they do not have a session" in {
          test(inSequence {
            mockAuthWithAllRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievalsMissingUserInfo)
            mockGetUserEnrolmentStatus()(Right(NotEnrolled))
            mockSessionCacheConnectorGet(Right(None))
          },
               validUserInfo.nino,
               testEmail
          )

        }
      }

    }

    "handling getEmailVerified" must {

      "return the email verified page" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), Some("email"), None))))
        }

        val result = controller.getEmailVerified(fakeRequestWithCSRFToken)
        status(result) shouldBe OK
        contentAsString(result) should include("Email address verified")
      }

      "redirect to check eligibility" when {

        "there is no session" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(None))
          }

          val result = controller.getEmailVerified(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
        }

      }

      "return an error" when {

        "there is no confirmed email in the session" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
          }

          val result = controller.getEmailVerified(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.NewApplicantUpdateEmailAddressController.verifyEmailError().url)
        }

        "there is no confirmed email in the session when there is no email for the user" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo.copy(userInfo = validUserInfo.copy(email = None)))), None, None))))
          }

          val result = controller.getEmailVerified(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EmailVerificationErrorController.verifyEmailErrorTryLater().url)
        }

        "the call to session cache fails" in {
          inSequence {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionCacheConnectorGet(Left(""))
          }

          val result = controller.getEmailVerified(FakeRequest())
          checkIsTechnicalErrorPage(result)
        }
      }

    }

    "handling getEmailUpdated" must {

        def doRequest(): Future[Result] =
          controller.getEmailUpdated()(fakeRequestWithCSRFToken)

      behave like commonBehaviour(doRequest)

      "show the email updated page otherwise" in {
        inSequence {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Right(eligibleWithValidUserInfo)), None, None))))
        }

        val result = doRequest()
        status(result) shouldBe OK
        contentAsString(result) should include("Email address verified")

      }

    }

    "handling emailUpdatedSubmit" must {

      "redirect to the create account page" in {
        val result = controller.emailUpdatedSubmit()(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountHelpToSavePage().url)

      }

    }

  }

  def commonBehaviour(doRequest: () ⇒ Future[Result]): Unit = { // scalastyle:ignore method.length

    "redirect to NS&I if they are already enrolled to HtS" in {
      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
        mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
      }

      val result = doRequest()
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
    }

    "redirect to 'You're not Eligible' if the session data indicates they are ineligible" in {
      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(Some(Left(randomIneligibility())), None, None))))
      }
      val result = doRequest()
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible().url)
    }

    "redirect to the eligibility check if there is no session data" in {
      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(None))
      }
      val result = doRequest()
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
    }

    "redirect to the eligibility check if the session data does not include an eligibility check response" in {
      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionCacheConnectorGet(Right(Some(HTSSession(None, None, None))))
      }
      val result = doRequest()
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility().url)
    }
  }
}
