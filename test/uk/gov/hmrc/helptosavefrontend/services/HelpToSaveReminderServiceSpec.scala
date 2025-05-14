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

package uk.gov.hmrc.helptosavefrontend.services

import cats.data.EitherT
import cats.instances.future._
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveReminderConnector
import uk.gov.hmrc.helptosavefrontend.controllers.ControllerSpecWithGuiceApp
import uk.gov.hmrc.helptosavefrontend.models.reminder.{CancelHtsUserReminder, HtsUserSchedule, UpdateReminderEmail}

import java.time.LocalDate
import scala.concurrent.Future

// scalastyle:off magic.number
class HelpToSaveReminderServiceSpec extends ControllerSpecWithGuiceApp with ScalaFutures {

  val htsReminderConnector = mock[HelpToSaveReminderConnector]

  val htsReminderService = new HelpToSaveReminderServiceImpl(htsReminderConnector)

  "The HelpToSaveReminderService" when {
    val nino: Nino = Nino("AE123456D")

    "update email" must {

      val htsUser =
        HtsUserSchedule(nino, "user@gmail.com", "Tyrion", "Lannister", true, Seq(1), LocalDate.parse("2000-01-01"))

      def mockupdateUser(htsUser: HtsUserSchedule)(result: Either[String, HtsUserSchedule]): Unit =
        when(htsReminderConnector.updateHtsUser(eqTo(htsUser))(any(), any()))
          .thenReturn(EitherT.fromEither[Future](result))

      "return a successful response" in {
        mockupdateUser(htsUser)(Right(htsUser))

        val result = htsReminderService.updateHtsUser(htsUser)
        await(result.value) shouldBe (Right(htsUser))
      }

    }

    "get Hts User" must {
      val ninoNew = "AE123456D"
      val htsUser =
        HtsUserSchedule(nino, "user@gmail.com", "Tyrion", "Lannister", true, Seq(1), LocalDate.parse("2000-01-01"))

      def mockGetHtsUser(result: Either[String, HtsUserSchedule]): Unit =
        when(htsReminderConnector.getHtsUser(eqTo(ninoNew))(any(), any()))
          .thenReturn(EitherT.fromEither[Future](result))

      "return a successful response" in {
        mockGetHtsUser(Right(htsUser))

        val result = htsReminderService.getHtsUser(ninoNew)
        await(result.value) shouldBe (Right(htsUser))
      }

    }

    "cancel Hts User Reminder" must {
      val ninoNew = "AE123456D"
      val cancelHtsUserReminder = CancelHtsUserReminder(ninoNew)

      def mockCancelHtsUserReminder(cancelHtsUserReminder: CancelHtsUserReminder)(result: Either[String, Unit]): Unit =
        when(htsReminderConnector.cancelHtsUserReminders(eqTo(cancelHtsUserReminder))(any(), any())).thenReturn(
          EitherT.fromEither[Future](
            result
          )
        )

      "return a successful response" in {
        mockCancelHtsUserReminder(cancelHtsUserReminder)(Right(()))

        val result = htsReminderService.cancelHtsUserReminders(cancelHtsUserReminder)
        await(result.value) shouldBe Right((()))
      }

      "cancel Hts User Reminder" must {
        val ninoNew = "AE123456D"
        val email = "test@user.com"
        val updateReminderEmail = UpdateReminderEmail(ninoNew, email, "Tyrion", "Lannister")

        def mockUpdateReminderEmail(updateReminderEmail: UpdateReminderEmail)(result: Either[String, Unit]): Unit =
          when(htsReminderConnector.updateReminderEmail(eqTo(updateReminderEmail))(any(), any()))
            .thenReturn(EitherT.fromEither[Future](result))

        "return a successful response" in {
          mockUpdateReminderEmail(updateReminderEmail)(Right(()))

          val result = htsReminderService.updateReminderEmail(updateReminderEmail)
          await(result.value) shouldBe Right((()))
        }

      }
    }
  }
}
