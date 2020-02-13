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

import java.util.Base64

import cats.data.EitherT
import cats.instances.future._
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.connectors.EmailVerificationConnector
import uk.gov.hmrc.helptosavefrontend.controllers.EmailControllerSpec.EligibleWithUserInfoOps
import uk.gov.hmrc.helptosavefrontend.forms.{BankDetails, ReminderFrequencyValidation, SortCode}
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavefrontend.models.HTSSession.EligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility._
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validUserInfo
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResultType
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError
import uk.gov.hmrc.helptosavefrontend.models.email.VerifyEmailError.AlreadyVerified
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIPayload
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveReminderService
import uk.gov.hmrc.helptosavefrontend.util.{Crypto, NINO}
import uk.gov.hmrc.helptosavefrontend.views.html.email._
import uk.gov.hmrc.helptosavefrontend.views.html.link_expired
import uk.gov.hmrc.helptosavefrontend.views.html.reminder.{reminder_confirmation, reminder_frequency_set}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
class ReminderControllerSpec
  extends ControllerSpecWithGuiceApp
  with AuthSupport
  with CSRFSupport
  with EnrolmentAndEligibilityCheckBehaviour
  with SessionStoreBehaviourSupport {

  override implicit val crypto: Crypto = mock[Crypto]
  implicit val reminderFrequencyValidation: ReminderFrequencyValidation = mock[ReminderFrequencyValidation]
  val encryptedEmail = "encrypted"
  val mockAuditor = mock[HTSAuditor]

  private val fakeRequest = FakeRequest("GET", "/")

  val mockHelpToSaveReminderService = mock[HelpToSaveReminderService]

  def mockEmailGet()(result: Either[String, Option[String]]): Unit =
    (mockHelpToSaveService
      .getConfirmedEmail()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def newController()(implicit crypto: Crypto, reminderFrequencyValidation: ReminderFrequencyValidation) = new ReminderController(
    mockHelpToSaveReminderService,
    mockHelpToSaveService,
    mockSessionStore,
    mockAuthConnector,
    mockMetrics,
    mockAuditor,
    testCpd,
    testMcc,
    testErrorHandler,
    injector.instanceOf[reminder_frequency_set],
    injector.instanceOf[reminder_confirmation]
  ) {

  }
  lazy val controller = newController()

  /*"The ReminderController" should {

    "handling getSelectRendersPage requests" in {

        def getSelectRendersPage(): Future[Result] = csrfAddToken(controller.getSelectRendersPage)(fakeRequest)

      inSequence {
        mockAuthWithNINORetrievalWithSuccess(AuthWithCL200)(
          mockedNINORetrieval)
        mockSessionStoreGet(Right(None))
        mockEnrolmentCheck()(Left(""))
      }
      val result = getSelectRendersPage()
      status(result) shouldBe 200
      contentAsString(result) should include("Select when you want to receive reminders")
      contentAsString(result) should include("/help-to-save/account-home/reminders-frequency-set")
    }
  }*/

}

