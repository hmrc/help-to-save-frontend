/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mockito.ArgumentMatchersSugar.*
import play.api.Configuration
import play.api.http.Status
import play.api.mvc.{Result => PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.controllers.RegisterController.EligibleWithInfo
import uk.gov.hmrc.helptosavefrontend.forms.{BankDetails, SortCode}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.{AuthProvider, AuthWithCL200}
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility._
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validUserInfo
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.account.AccountNumber
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResponse
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResultType.Eligible
import uk.gov.hmrc.helptosavefrontend.models.reminder.HtsUserSchedule
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveServiceImpl.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.util.Crypto
import uk.gov.hmrc.helptosavefrontend.views.html.cannot_check_details
import uk.gov.hmrc.helptosavefrontend.views.html.register._

import java.time._
import java.util.UUID
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class RegisterControllerSpec
    extends ControllerSpecWithGuiceApp with AuthSupport with CSRFSupport with EnrolmentAndEligibilityCheckBehaviour
    with SessionStoreBehaviourSupport {

  val january1970Clock = Clock.fixed(Instant.ofEpochMilli(0L), ZoneId.of("Z"))
  def newController(earlyCapCheck: Boolean)(implicit crypto: Crypto): RegisterController = {
    implicit lazy val appConfig: FrontendAppConfig =
      buildFakeApplication(Configuration("enable-early-cap-check" -> earlyCapCheck)).injector
        .instanceOf[FrontendAppConfig]

    new RegisterController(
      mockHelpToSaveService,
      mockHelpToSaveReminderService,
      mockSessionStore,
      mockAuthConnector,
      mockMetrics,
      injector.instanceOf[HTSAuditor],
      testCpd,
      testMcc,
      testErrorHandler,
      testMaintenanceSchedule,
      injector.instanceOf[create_account],
      injector.instanceOf[daily_cap_reached],
      injector.instanceOf[total_cap_reached],
      injector.instanceOf[service_unavailable],
      injector.instanceOf[details_are_incorrect],
      injector.instanceOf[account_created],
      injector.instanceOf[create_account_error],
      injector.instanceOf[create_account_error_bank_details],
      injector.instanceOf[cannot_check_details]
    ) {
      override val clock = january1970Clock
    }
  }

  lazy val controller: RegisterController = newController(earlyCapCheck = false)(crypto)

  private val fakeRequest = FakeRequest("GET", "/")

  def govukBackLink(url: String): String = s"""<a href="${url}" class="govuk-back-link" id="back">Back</a>"""

  def mockEmailUpdate(email: String)(result: Either[String, Unit]): Unit =
    mockHelpToSaveService.storeConfirmedEmail(email)(*, *) returns EitherT.fromEither[Future](result)

  def mockAccountCreationAllowed(result: Either[String, UserCapResponse]): Unit =
    mockHelpToSaveService.isAccountCreationAllowed()(*, *) returns EitherT.fromEither[Future](result)

  def mockDecrypt(expected: String)(result: Option[String]) =
    crypto.decrypt(expected) returns result.fold[Try[String]](Failure(new Exception))(Success.apply)

  def mockUpdateHtsUserPost(htsUser: HtsUserSchedule)(result: Either[String, HtsUserSchedule]): Unit =
    mockHelpToSaveReminderService.updateHtsUser(htsUser)(*, *) returns EitherT.fromEither[Future](result)

  def checkRedirectIfNoEmailInSession(doRequest: => Future[PlayResult]): Unit =
    "redirect to the give email page if the session data does not contain an email for the user" in {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
        mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
        mockSessionStoreGet(Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None))))

      val result = doRequest
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.EmailController.getSelectEmailPage.url)
    }

  "The RegisterController" when {

    "handling getCannotCheckDetailsPage" must {

      "return the cannot check details page" in {
        val result = controller.getCannotCheckDetailsPage(FakeRequest())
        status(result) shouldBe Status.OK
        contentAsString(result) should include(
          "This is because you cannot use the Government Gateway account you signed into"
        )
      }

    }

    "handling getDailyCapReachedPage" must {

      "return the daily cap reached page" in {
        mockAuthWithNoRetrievals(AuthProvider)

        val result = controller.getDailyCapReachedPage(FakeRequest())
        status(result) shouldBe Status.OK
        contentAsString(result) should include(
          "We have a limit on the number of people who can open an account each day"
        )
      }

    }

    "handling getTotalCapReachedPage" must {

      "return the total cap reached page" in {
        mockAuthWithNoRetrievals(AuthProvider)

        val result = controller.getTotalCapReachedPage(FakeRequest())
        status(result) shouldBe Status.OK
        contentAsString(result) should include(
          "We have a limit on the number of people who can open an account at the moment"
        )
      }

    }

    "handling service_unavailable page" must {

      "return the account create disabled page" in {
        mockAuthWithNoRetrievals(AuthProvider)

        val result = controller.getServiceUnavailablePage(FakeRequest())
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Service unavailable")
      }

    }

    "handling service_outage page" must {

      "return the account create disabled page" in {
        val currentDate = LocalDateTime.now().toString
        val result = controller.getServiceOutagePage(currentDate)(fakeRequest)
        status(result) shouldBe Status.OK
        contentAsString(result) should include("Sorry, the service is unavailable")
      }

    }

    "handling getDetailsAreIncorrect" must {

      "return the details are incorrect page" in {
        mockAuthWithNoRetrievals(AuthProvider)

        val result = controller.getDetailsAreIncorrect(FakeRequest())
        status(result) shouldBe Status.OK
        contentAsString(result) should include("We need your correct details")
        contentAsString(result) should include(govukBackLink("/help-to-save/create-account"))
      }
    }

    "handling a getCreateAccountPage" must {

      val email = "email"

      val bankDetails = BankDetails(SortCode(1, 2, 3, 4, 5, 6), "12345678", None, "test user name")

      val userInfo = eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 6)

      def doRequest(): Future[PlayResult] =
        csrfAddToken(controller.getCreateAccountPage())(fakeRequest)

      behave like commonEnrolmentAndSessionBehaviour(() => doRequest())

      checkRedirectIfNoEmailInSession(doRequest())

      "show the user the create account page if the session data contains a confirmed email" in {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(
              Some(
                HTSSession(
                  Some(Right(eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 6))),
                  Some(email),
                  None,
                  None,
                  None,
                  Some(bankDetails)
                )
              )
            )
          )
          mockSessionStorePut(
            HTSSession(Some(Right(userInfo)), Some(email), None, None, None, Some(bankDetails), accountNumber = None)
          )(Right(()))

        val result = doRequest()
        status(result) shouldBe OK
        contentAsString(result) should include("Accept and create account")
        contentAsString(result) should include(govukBackLink("/help-to-save/enter-uk-bank-details"))
      }

      "show an error page if the eligibility reason cannot be parsed" in {
        val userInfo = eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 999)
        val eligibilityCheckResult = randomEligibility().value.eligibilityCheckResult.copy(reasonCode = 999)
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(
              Some(
                HTSSession(
                  Some(
                    Right(
                      randomEligibleWithUserInfo(validUserInfo)
                        .copy(
                          eligible = Eligible(
                            EligibilityCheckResponse(eligibilityCheckResult, randomEligibility().value.threshold)
                          )
                        )
                    )
                  ),
                  Some(email),
                  None
                )
              )
            )
          )
          mockSessionStorePut(
            HTSSession(Some(Right(userInfo)), Some(email), None, None, None, None, accountNumber = None)
          )(Right(()))

        val result = doRequest()
        checkIsTechnicalErrorPage(result)
      }

      "show the appropriate page content for when user is eligible with reason code 6: UCClaimantAndIncomeSufficient" in {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(
              Some(
                HTSSession(
                  Some(Right(eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 6))),
                  Some(email),
                  None,
                  None,
                  None,
                  Some(bankDetails)
                )
              )
            )
          )
          mockSessionStorePut(
            HTSSession(Some(Right(userInfo)), Some(email), None, None, None, Some(bankDetails), accountNumber = None)
          )(Right(()))

        val result = doRequest()
        status(result) shouldBe OK
        contentAsString(result) should include("you will tell us each time you leave the UK for 4 weeks or more")
      }

      "show the appropriate page content for when user is eligible with reason code 7: UCClaimantButNoWTC" in {
        val userInfo = eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 7)
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(
              Some(
                HTSSession(
                  Some(Right(eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 7))),
                  Some(email),
                  None,
                  None,
                  None,
                  Some(bankDetails)
                )
              )
            )
          )
          mockSessionStorePut(
            HTSSession(Some(Right(userInfo)), Some(email), None, None, None, Some(bankDetails), accountNumber = None)
          )(Right(()))

        val result = doRequest()
        status(result) shouldBe OK
        contentAsString(result) should include("you will tell us each time you leave the UK for 8 weeks or more")
      }

      "show the appropriate page content for when user is eligible with reason code 8: UCClaimantAndWTC" in {
        val userInfo = eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 8)
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(
              Some(
                HTSSession(
                  Some(Right(eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 8))),
                  Some(email),
                  None,
                  None,
                  None,
                  Some(bankDetails)
                )
              )
            )
          )
          mockSessionStorePut(
            HTSSession(Some(Right(userInfo)), Some(email), None, None, None, Some(bankDetails), accountNumber = None)
          )(Right(()))

        val result = doRequest()
        status(result) shouldBe OK
        contentAsString(result) should include("you will tell us each time you leave the UK for 4 weeks or more")
      }

      "show user not eligible page if the user is not eligible" in {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(Some(HTSSession(Some(Left(randomIneligibility())), None, None, None, None, Some(bankDetails))))
          )
        val result = doRequest()

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible.url)
      }

      "handle the case when there are no bank details stored in the session" in {
        val eligibilityResult = Some(Right(eligibleSpecificReasonCodeWithUserInfo(validUserInfo, 6)))
        val email = "valid@email.com"
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(eligibilityResult, Some(email), None))))
          mockSessionStorePut(
            HTSSession(Some(Right(userInfo)), Some(email), None, None, None, None, accountNumber = None)
          )(Right(()))
        val result = doRequest()

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.BankAccountController.getBankDetailsPage.url)
      }

    }

    "creating an account" must {

      def doCreateAccountRequest(): Future[PlayResult] = csrfAddToken(controller.createAccount)(fakeRequest)

      HtsUserSchedule(Nino(nino), "email", firstName, lastName, true, Seq(1), LocalDate.now())

      behave like commonEnrolmentAndSessionBehaviour(doCreateAccountRequest)

      checkRedirectIfNoEmailInSession(doCreateAccountRequest())

      "retrieve the user info from session cache and indicate to the user that the creation was successful " +
        "and enrol the user if the creation was successful" in {

          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails))))
          )
          mockCreateAccount(createAccountRequest)(Right(SubmissionSuccess(AccountNumber(Some(accountNumber)))))
          mockSessionStorePut(
            HTSSession(
              Some(Right(userInfo)),
              Some(confirmedEmail),
              None,
              None,
              None,
              Some(bankDetails),
              accountNumber = Some(accountNumber)
            )
          )(Right(()))

        val result = doCreateAccountRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getAccountCreatedPage.url)
      }

      "indicate to the user that account creation was successful " +
        "even if the user couldn't be enrolled into hts at this time" in {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails))))
          )
          mockCreateAccount(createAccountRequest)(Right(SubmissionSuccess(AccountNumber(Some(accountNumber)))))
          mockSessionStorePut(
            HTSSession(
              Some(Right(userInfo)),
              Some(confirmedEmail),
              None,
              None,
              None,
              Some(bankDetails),
              accountNumber = Some(accountNumber)
            )
          )(Right(()))

        val result = doCreateAccountRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.getAccountCreatedPage.url)
      }

      "not update user counts but enrol the user if the user already had an account" in {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails))))
          )
          mockCreateAccount(createAccountRequest)(Right(SubmissionSuccess(AccountNumber(None))))

        val result = doCreateAccountRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
      }

      "redirect the user to nsi when they already have an account" in {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails))))
          )
          mockCreateAccount(createAccountRequest)(Right(SubmissionSuccess(AccountNumber(None))))

        val result = doCreateAccountRequest()

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(appConfig.nsiManageAccountUrl)
      }

      "redirect the user to the confirm details page if the session indicates they have not done so already" in {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), None, None)))
          )

        val result = doCreateAccountRequest()
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.EmailController.getSelectEmailPage.url)
      }

      "redirect user to bank_details page if the session doesn't contain bank details" in {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None))))

        val result = doCreateAccountRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.BankAccountController.getBankDetailsPage.url)
      }

      "redirect to the create account error page" when {
        "the help to save service returns with an error" in {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionStoreGet(
              Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails))))
            )
            mockCreateAccount(createAccountRequest)(Left(SubmissionFailure(None, "Uh oh", "Uh oh")))

          val result = doCreateAccountRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountErrorPage.url)
        }

        "there is an error writing to session" in {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionStoreGet(
              Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails))))
            )
            mockCreateAccount(createAccountRequest)(Right(SubmissionSuccess(AccountNumber(Some(accountNumber)))))
            mockSessionStorePut(
              HTSSession(
                Some(Right(userInfo)),
                Some(confirmedEmail),
                None,
                None,
                None,
                Some(bankDetails),
                accountNumber = Some(accountNumber)
              )
            )(Left(""))

          val result = doCreateAccountRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountErrorPage.url)
        }
      }

      "redirect to the create account bank details error page" when {
        "the errorMessageId received in the response from nsi is ZYRC0703" in {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionStoreGet(
              Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails))))
            )
            mockCreateAccount(createAccountRequest)(Left(SubmissionFailure(Some("ZYRC0703"), "Uh oh", "Uh oh")))

          val result = doCreateAccountRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountErrorBankDetailsPage.url)
        }

        "the errorMessageId received in the response from nsi is ZYRC0707" in {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
            mockSessionStoreGet(
              Right(Some(HTSSession(Some(Right(userInfo)), Some(confirmedEmail), None, None, None, Some(bankDetails))))
            )
            mockCreateAccount(createAccountRequest)(Left(SubmissionFailure(Some("ZYRC0707"), "Uh oh", "Uh oh")))

          val result = doCreateAccountRequest()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.RegisterController.getCreateAccountErrorBankDetailsPage.url)
        }
      }

    }

    "handling getAccountCreatedPage" must {

      def getAccountCreatedPage() = csrfAddToken(controller.getAccountCreatedPage())(fakeRequest)

      "show the page correctly if the person is enrolled to HTS and the session has an account number in it" in {
        val accountNumber = UUID.randomUUID().toString

          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockSessionStoreGet(
            Right(Some(HTSSession(None, Some("email@gmail.com"), None, accountNumber = Some(accountNumber))))
          )

        val result = getAccountCreatedPage()
        status(result) shouldBe OK
        contentAsString(result) should include("Account created")
        contentAsString(result) should include(accountNumber)
        contentAsString(result) should include("""You have until <strong class="bold">31 January 1970</strong> to pay in money this month""")
      }

      "redirect to check eligibility" when {

        "there is no session data" in {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
            mockSessionStoreGet(Right(None))

          val result = getAccountCreatedPage()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
        }

        "there is no account number in session" in {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
            mockSessionStoreGet(Right(Some(HTSSession(None, None, None, accountNumber = None))))

          val result = getAccountCreatedPage()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
        }

        "the person is not enrolled to HTS" in {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))

          val result = getAccountCreatedPage()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
        }

        "there is no email in the session" in {
          val accountNumber = UUID.randomUUID().toString

            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
            mockSessionStoreGet(Right(Some(HTSSession(None, None, None, accountNumber = Some(accountNumber)))))

          val result = getAccountCreatedPage()
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getCheckEligibility.url)
        }

      }

      "show an error page" when {

        "the enrolment status cannot be retrieved" in {
            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Left(""))

          val result = getAccountCreatedPage()
          checkIsTechnicalErrorPage(result)
        }

        "the session data could not be retrieved" in {

            mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
            mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
            mockSessionStoreGet(Left(""))

          val result = getAccountCreatedPage()
          checkIsTechnicalErrorPage(result)
        }

      }

    }

    "handling accessOrPayIn" must {
      def fRequest(payIn: String) =  fakeRequest.withMethod("POST").withFormUrlEncodedBody("payInNow" -> payIn)
      def postAccessOrPayIn(payIn: String) = csrfAddToken(controller.accessOrPayIn())(fRequest(payIn))

      "show errors on the page" in {
        val accountNumber = UUID.randomUUID().toString

          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockSessionStoreGet(
            Right(Some(HTSSession(None, Some("email@gmail.com"), None, accountNumber = Some(accountNumber))))
          )

        val result = postAccessOrPayIn("")
        status(result) shouldBe OK
        contentAsString(result) should include("There is a problem")
        contentAsString(result) should include("Select whether you want to pay in your first deposit now")
      }

      "redirect to PayIn" in {
        val accountNumber = UUID.randomUUID().toString

          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockSessionStoreGet(
            Right(Some(HTSSession(None, Some("email@gmail.com"), None, accountNumber = Some(accountNumber))))
          )

        val result = postAccessOrPayIn("true")
        status(result) shouldBe 303
        redirectLocation(result) shouldBe(Some(routes.AccessAccountController.payIn.url))

      }

      "redirect to AccessAccount" in {
        val accountNumber = UUID.randomUUID().toString

          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.Enrolled(true)))
          mockSessionStoreGet(
            Right(Some(HTSSession(None, Some("email@gmail.com"), None, accountNumber = Some(accountNumber))))
          )

        val result = postAccessOrPayIn("false")
        status(result) shouldBe 303
        redirectLocation(result) shouldBe(Some(routes.AccessAccountController.accessAccount.url))

      }

    }

    "handling getCreateAccountErrorPage" must {
      val confirmedEmail = "confirmed"

      def doRequest(): Future[PlayResult] = controller.getCreateAccountErrorPage(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(doRequest)

      "show the error page" in {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), Some(confirmedEmail), None)))
          )

        val result = doRequest()
        contentAsString(result) should include("We cannot create a Help to Save account for you at the moment")
      }
    }

    "handling getCreateAccountErrorBankDetailsPage" must {
      val confirmedEmail = "confirmed"

      def doRequest(): Future[PlayResult] = controller.getCreateAccountErrorBankDetailsPage(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(doRequest)

      "show the error page" in {
          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(
            Right(Some(HTSSession(Some(Right(randomEligibleWithUserInfo(validUserInfo))), Some(confirmedEmail), None)))
          )

        val result = doRequest()
        contentAsString(result) should include("There is a problem with your bank details")
      }
    }

    "handling checkYourDetails page" must {

      val bankDetails = BankDetails(SortCode(1, 2, 3, 4, 5, 6), "12345678", None, "test user name")

      def doRequest(): Future[PlayResult] = csrfAddToken(controller.getCreateAccountPage())(fakeRequest)

      behave like commonEnrolmentAndSessionBehaviour(() => doRequest())

      checkRedirectIfNoEmailInSession(doRequest())

      "show the details page for valid users" in {
        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
        val session = HTSSession(eligibilityResult, Some("valid@email.com"), None, None, None, Some(bankDetails))

          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(session)))
          mockSessionStorePut(
            HTSSession(
              eligibilityResult,
              Some("valid@email.com"),
              None,
              None,
              None,
              Some(bankDetails),
              accountNumber = None
            )
          )(Right(()))
        val result = doRequest()

        status(result) shouldBe 200
        contentAsString(result) should include("Create a Help to Save account")
      }

      "show user not eligible page if the user is not eligible" in {
        val session = HTSSession(Some(Left(randomIneligibility())), None, None, None, None, Some(bankDetails))

          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(session)))
        val result = doRequest()

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EligibilityCheckController.getIsNotEligible.url)
      }

      "handle the case when there are no bank details stored in the session" in {
        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
        val session = HTSSession(eligibilityResult, Some("valid@email.com"), None)

          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(session)))
          mockSessionStorePut(session)(Right(()))
        val result = doRequest()

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.BankAccountController.getBankDetailsPage.url)
      }
    }

    "handling changeEmail" must {
      def doRequest(): Future[PlayResult] = controller.changeEmail()(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(() => doRequest())

      "write a new session and redirect to the select email page" in {
        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
        val session = HTSSession(eligibilityResult, Some("valid@email.com"), None)

          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(session)))
          mockSessionStorePut(session.copy(changingDetails = true))(Right(()))
        val result = doRequest()

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.EmailController.getSelectEmailPage.url)
      }

    }

    "handling changeBankDetails" must {
      def doRequest(): Future[PlayResult] = controller.changeBankDetails()(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(() => doRequest())

      "write a new session and redirect to bank details page" in {
        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
        val session = HTSSession(eligibilityResult, Some("valid@email.com"), None)

          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(session)))
          mockSessionStorePut(session.copy(changingDetails = true))(Right(()))
        val result = doRequest()

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.BankAccountController.getBankDetailsPage.url)
      }

    }
    "handling changeReminders" must {
      def doRequest(): Future[PlayResult] = controller.changeReminder()(FakeRequest())

      behave like commonEnrolmentAndSessionBehaviour(() => doRequest())

      "write a new session and redirect to bank details page" in {
        val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
        val session = HTSSession(eligibilityResult, Some("valid@email.com"), None)

          mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(mockedNINORetrieval)
          mockEnrolmentCheck()(Right(EnrolmentStatus.NotEnrolled))
          mockSessionStoreGet(Right(Some(session)))
          mockSessionStorePut(session.copy(changingDetails = true))(Right(()))
        val result = doRequest()

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.ReminderController.getApplySavingsReminderPage.url)
      }
    }

    "handling updateReminders" must {
      val eligibilityResult = Some(Right(randomEligibleWithUserInfo(validUserInfo)))
      val eligibilityInfo = EligibleWithInfo(
        randomEligibleWithUserInfo(validUserInfo),
        "email",
        HTSSession(eligibilityResult, Some("valid@email.com"), None)
      )
      val fakeRequestWithNoBody = FakeRequest("POST", "/").withFormUrlEncodedBody("reminderFrequency" -> "1st")

      def doRequest(): Future[PlayResult] =
        controller.processReminderServiceRequest(Some("1st"), eligibilityInfo.userInfo.userInfo.nino, eligibilityInfo)(
          fakeRequestWithNoBody
        )
      val htsUserForUpdate = HtsUserSchedule(Nino(nino), "email", firstName, lastName, true, Seq(1), LocalDate.now())

      "write a new session and redirect to Account Page " in {
          mockUpdateHtsUserPost(htsUserForUpdate)(Right(htsUserForUpdate))
        val result = doRequest()

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some(routes.RegisterController.getAccountCreatedPage.url)
      }

    }
  }
}
