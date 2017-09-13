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

package uk.gov.hmrc.helptosavefrontend.controllers.email

import cats.data.EitherT
import cats.instances.future._
import play.api.i18n.MessagesApi
import play.api.libs.json.Reads
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.controllers.AuthSupport
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithCL200
import uk.gov.hmrc.helptosavefrontend.models.{EnrolmentStatus, HTSSession}
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AccountHolderUpdateEmailAddressControllerSpec extends AuthSupport {

  val mockHelpToSaveService = mock[HelpToSaveService]

  def mockEnrolmentCheck(input: NINO)(result: Either[String, EnrolmentStatus]): Unit =
    (mockHelpToSaveService.getUserEnrolmentStatus(_: NINO)(_: HeaderCarrier))
      .expects(input, *)
      .returning(EitherT.fromEither[Future](result))

  def mockEmailGet(input: NINO)(result: Either[String, Option[String]]): Unit =
    (mockHelpToSaveService.getConfirmedEmail(_: NINO)(_: HeaderCarrier))
      .expects(input, *)
      .returning(EitherT.fromEither[Future](result))

  lazy val controller = new AccountHolderUpdateEmailAddressController(
    mockHelpToSaveService,
    mockAuthConnector
  )(fakeApplication, fakeApplication.injector.instanceOf[MessagesApi], ec) {
    override val authConnector = mockAuthConnector
  }

  "The AccountHolderUpdateEmailAddressController" when {

    "handling requests to update email addresses" must {

        def getUpdateYourEmailAddress(): Future[Result] =
          controller.getUpdateYourEmailAddress()(FakeRequest())

      behave like (commonEnrolmentBehaviour(() ⇒ getUpdateYourEmailAddress()))

      "show a page which allows the user to change their email if they are already " +
        "enrolled and we have an email stored for them" in {
          inSequence{
            mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
            mockEnrolmentCheck(nino)(Right(Enrolled(true)))
            mockEmailGet(nino)(Right(Some("email")))
          }

          val result = getUpdateYourEmailAddress()
          status(result) shouldBe OK
          contentAsString(result) should include("Update your email")
        }
    }
  }

  def commonEnrolmentBehaviour(getResult: () ⇒ Future[Result]) = { // scalastyle:ignore method.length

    "return an error" when {

      "the user has no NINO" in {
        mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedMissingNinoEnrolment)

        status(getResult()) shouldBe INTERNAL_SERVER_ERROR
      }

      "there is an error getting the enrolment status" in {
        inSequence{
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck(nino)(Left(""))
        }

        status(getResult()) shouldBe INTERNAL_SERVER_ERROR
      }

      "there is an error getting the confirmed email" in {
        inSequence{
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck(nino)(Right(Enrolled(true)))
          mockEmailGet(nino)(Left(""))
        }

        status(getResult()) shouldBe INTERNAL_SERVER_ERROR
      }

      "the user is not enrolled" in {
        inSequence{
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck(nino)(Right(NotEnrolled))
          mockEmailGet(nino)(Right(Some("email")))
        }

        status(getResult()) shouldBe INTERNAL_SERVER_ERROR

      }

      "the user is enrolled but has no stored email" in {
        inSequence{
          mockAuthWithRetrievalsWithSuccess(AuthWithCL200)(mockedRetrievals)
          mockEnrolmentCheck(nino)(Right(Enrolled(true)))
          mockEmailGet(nino)(Right(None))
        }

        status(getResult()) shouldBe INTERNAL_SERVER_ERROR
      }

    }
  }

}
