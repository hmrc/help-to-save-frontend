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

package uk.gov.hmrc.helptosavefrontend.services

import java.time.LocalDate
import java.util.UUID

import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.stream.impl.fusing.Fold
import cats.data.EitherT
import cats.instances.future._
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.connectors.{HelpToSaveConnector, HelpToSaveReminderConnector}
import uk.gov.hmrc.helptosavefrontend.controllers.ControllerSpecWithGuiceApp
import uk.gov.hmrc.helptosavefrontend.models.TestData.Eligibility.randomEligibility
import uk.gov.hmrc.helptosavefrontend.models.TestData.UserData.validNSIPayload
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.account.{Account, AccountNumber, Blocking}
import uk.gov.hmrc.helptosavefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavefrontend.models.reminder.{CancelHtsUserReminder, HtsUser, UpdateReminderEmail}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIPayload
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveServiceImpl.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

// scalastyle:off magic.number
class HelpToSaveReminderServiceSpec extends ControllerSpecWithGuiceApp with ScalaFutures {

  val htsReminderConnector = mock[HelpToSaveReminderConnector]

  val htsReminderService = new HelpToSaveReminderServiceImpl(htsReminderConnector)

  "The HelpToSaveReminderService" when {
    val nino: Nino = Nino("AE123456D")

    "update email" must {

      val htsUser = HtsUser(nino, "user@gmail.com", "new user", true, Seq(1), LocalDate.parse("2000-01-01"), 1)

      def mockupdateUser(htsUser: HtsUser)(result: Either[String, HtsUser]): Unit = {
        (htsReminderConnector.updateHtsUser(_: HtsUser)(_: HeaderCarrier, _: ExecutionContext))
          .expects(htsUser, *, *)
          .returning(EitherT.fromEither[Future](result))
      }

      "return a successful response" in {
        mockupdateUser(htsUser)(Right(htsUser))

        val result = htsReminderService.updateHtsUser(htsUser)
        await(result.value) shouldBe (Right(htsUser))
      }

    }

    "get Hts User" must {
      val ninoNew = "AE123456D"
      val htsUser = HtsUser(nino, "user@gmail.com", "new user", true, Seq(1), LocalDate.parse("2000-01-01"), 1)

      def mockGetHtsUser(nino: String)(result: Either[String, HtsUser]): Unit = {
        (htsReminderConnector.getHtsUser(_: String)(_: HeaderCarrier, _: ExecutionContext))
          .expects(ninoNew, *, *)
          .returning(EitherT.fromEither[Future](result))
      }

      "return a successful response" in {
        mockGetHtsUser(ninoNew)(Right(htsUser))

        val result = htsReminderService.getHtsUser(ninoNew)
        await(result.value) shouldBe (Right(htsUser))
      }

    }

    "cancel Hts User Reminder" must {
      val ninoNew = "AE123456D"
      val cancelHtsUserReminder = CancelHtsUserReminder(ninoNew)

      def mockCancelHtsUserReminder(cancelHtsUserReminder: CancelHtsUserReminder)(result: Either[String, Unit]): Unit = {
        (htsReminderConnector.cancelHtsUserReminders(_: CancelHtsUserReminder)(_: HeaderCarrier, _: ExecutionContext))
          .expects(cancelHtsUserReminder, *, *)
          .returning(EitherT.fromEither[Future]((result)))
      }

      "return a successful response" in {
        mockCancelHtsUserReminder(cancelHtsUserReminder)(Right(()))

        val result = htsReminderService.cancelHtsUserReminders(cancelHtsUserReminder)
        await(result.value) shouldBe Right((()))
      }

      "cancel Hts User Reminder" must {
        val ninoNew = "AE123456D"
        val email = "test@user.com"
        val updateReminderEmail = UpdateReminderEmail(ninoNew, email)


        def mockUpdateReminderEmail(updateReminderEmail: UpdateReminderEmail)(result: Either[String, Unit]): Unit = {
          (htsReminderConnector.updateReminderEmail(_: UpdateReminderEmail)(_: HeaderCarrier, _: ExecutionContext))
            .expects(updateReminderEmail, *, *)
            .returning(EitherT.fromEither[Future]((result)))
        }

        "return a successful response" in {
          mockUpdateReminderEmail(updateReminderEmail)(Right(()))

          val result = htsReminderService.updateReminderEmail(updateReminderEmail)
          await(result.value) shouldBe Right((()))
        }

      }
    }
  }
}
