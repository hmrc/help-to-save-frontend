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

package uk.gov.hmrc.helptosavefrontend.connectors

import com.typesafe.config.ConfigFactory
import org.mockito.IdiomaticMockito
import org.scalatest.EitherValues
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.{Application, Configuration}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.controllers.ControllerSpecBase
import uk.gov.hmrc.helptosavefrontend.models.reminder.{CancelHtsUserReminder, HtsUserSchedule, UpdateReminderEmail}
import uk.gov.hmrc.helptosavefrontend.util.WireMockMethods
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.time.LocalDate
import scala.concurrent.ExecutionContext

// scalastyle:off magic.number
class HelpToSaveReminderConnectorSpec
    extends ControllerSpecBase with IdiomaticMockito with WireMockSupport with WireMockMethods with GuiceOneAppPerSuite
    with EitherValues {

  private val config = Configuration(
    ConfigFactory.parseString(
      s"""
         |microservice {
         |  services {
         |      help-to-save-reminder {
         |      protocol = http
         |      host     = $wireMockHost
         |      port     = $wireMockPort
         |    }
         |  }
         |}
         |""".stripMargin
    )
  )

  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(config).build()
  lazy val connector: HelpToSaveReminderConnector = app.injector.instanceOf[HelpToSaveReminderConnectorImpl]
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  val UpdateHtsURL = "/help-to-save-reminder/update-htsuser-entity"

  def getHtsReminderUserURL(nino: String) = s"/help-to-save-reminder/gethtsuser/$nino"

  val cancelHtsReminderURL = "/help-to-save-reminder/delete-htsuser-entity"

  val emailUpdateHtsReminderURL = "/help-to-save-reminder/update-htsuser-email"

  val emptyBody = ""
  val emptyHeaders: Map[String, Seq[String]] = Map.empty
  implicit val unitFormat: Format[Unit] = new Format[Unit] {
    override def writes(o: Unit) = JsNull

    override def reads(json: JsValue) = json match {
      case JsNull => JsSuccess(())
      case _      => JsError("JSON was not null")
    }
  }

  "validating HtsUser" must {

    val nino: Nino = Nino("AE123456D")

    "return http response as it is to the caller" in {
      val htsUser =
        HtsUserSchedule(nino, "user@gmail.com", "Tyrion", "Lannister", true, Seq(1), LocalDate.parse("2000-01-01"))

      val response = HttpResponse(200, Json.toJson(htsUser), emptyHeaders)

      when(
        POST,
        UpdateHtsURL,
        body = Some(Json.toJson(htsUser).toString())
      ).thenReturn(
        response.status,
        response.body
      )

      val result = connector.updateHtsUser(htsUser)
      await(result.value) should equal(Right(htsUser))

    }
  }

  "get HtsUser" must {

    val ninoNew = "AE123456D"
    val nino: Nino = Nino("AE123456D")

    "return http response as it is to the caller" in {
      val htsUser =
        HtsUserSchedule(nino, "user@gmail.com", "Tyrion", "Lannister", true, Seq(1), LocalDate.parse("2000-01-01"))

      val response =
        HttpResponse(200, Json.toJson(htsUser), emptyHeaders)
      when(
        GET,
        getHtsReminderUserURL(ninoNew)
      ).thenReturn(
        response.status,
        response.body
      )

      val result = connector.getHtsUser(ninoNew)
      await(result.value) should equal(Right(htsUser))

    }
  }

  "cancel HtsUser Reminder" must {

    val ninoNew = "AE123456D"
    val cancelHtsUserReminder = CancelHtsUserReminder(ninoNew)

    "return http response as it is to the caller" in {
      val response = HttpResponse(200, emptyBody)
      when(
        POST,
        cancelHtsReminderURL,
        body = Some(Json.toJson(cancelHtsUserReminder).toString())
      ).thenReturn(
        response.status,
        response.body
      )
      val result = connector.cancelHtsUserReminders(cancelHtsUserReminder)
      await(result.value) should equal(Right(()))
    }
    "return http response as it is to the caller when not modified" in {
      val response = HttpResponse(304, emptyBody)
      when(
        POST,
        cancelHtsReminderURL,
        body = Some(Json.toJson(cancelHtsUserReminder).toString())
      ).thenReturn(
        response.status,
        response.body
      )
      val result = connector.cancelHtsUserReminders(cancelHtsUserReminder)
      await(result.value) should equal(Right(()))
    }
    "fail when unexpected response received" in {
      val response = HttpResponse(400, emptyBody)
      when(
        POST,
        cancelHtsReminderURL,
        body = Some(Json.toJson(cancelHtsUserReminder).toString())
      ).thenReturn(
        response.status,
        response.body
      )
      val result = connector.cancelHtsUserReminders(cancelHtsUserReminder)
      await(result.value).isLeft should equal(true)
    }
  }

  "Update HtsUser Reminder Email" must {

    val ninoNew = "AE123456D"
    val email = "test@user.com"
    val updateReminderEmail = UpdateReminderEmail(ninoNew, email, "Tyrion", "Lannister")

    "return http response as it is to the caller" in {
      val response =
        HttpResponse(200, emptyBody)
      when(
        POST,
        emailUpdateHtsReminderURL,
        body = Some(Json.toJson(updateReminderEmail).toString())
      ).thenReturn(
        response.status,
        response.body
      )
      val result = connector.updateReminderEmail(updateReminderEmail)
      await(result.value) should equal(Right(()))

    }
  }

}
