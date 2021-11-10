/*
 * Copyright 2021 HM Revenue & Customs
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

import java.time.LocalDate

import cats.data.EitherT
import cats.instances.int._
import cats.syntax.eq._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.helptosavefrontend.controllers.ControllerSpecWithGuiceApp
import uk.gov.hmrc.helptosavefrontend.models.reminder.{CancelHtsUserReminder, HtsUserSchedule, UpdateReminderEmail}
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.Future

// scalastyle:off magic.number
class HelpToSaveReminderConnectorSpec
    extends ControllerSpecWithGuiceApp with HttpSupport with ScalaCheckDrivenPropertyChecks {

  lazy val connector: HelpToSaveReminderConnector = new HelpToSaveReminderConnectorImpl(mockHttp)

  val htsReminderURL = "http://localhost:7008"

  val UpdateHtsURL =
    s"$htsReminderURL/help-to-save-reminder/update-htsuser-entity"

  def getHtsReminderUserURL(nino: String) = s"$htsReminderURL/help-to-save-reminder/gethtsuser/$nino"

  val cancelHtsReminderURL = s"$htsReminderURL/help-to-save-reminder/delete-htsuser-entity"

  val emailUpdateHtsReminderURL = s"$htsReminderURL/help-to-save-reminder/update-htsuser-email"

  implicit val unitFormat: Format[Unit] = new Format[Unit] {
    override def writes(o: Unit) = JsNull

    override def reads(json: JsValue) = json match {
      case JsNull ⇒ JsSuccess(())
      case _ ⇒ JsError("JSON was not null")
    }
  }

  "validating HtsUser" must {

    val nino: Nino = Nino("AE123456D")

    "return http response as it is to the caller" in {
      val htsUser =
        HtsUserSchedule(nino, "user@gmail.com", "Tyrion", "Lannister", true, Seq(1), LocalDate.parse("2000-01-01"))

      val response =
        HttpResponse(200, Some(Json.toJson(htsUser)))
      mockPost(UpdateHtsURL, Map.empty, htsUser)(Some(response))
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
        HttpResponse(200, Some(Json.toJson(htsUser)))
      mockGet(getHtsReminderUserURL(ninoNew), Map.empty)(Some(response))
      val result = connector.getHtsUser(ninoNew)
      await(result.value) should equal(Right(htsUser))

    }
  }
  "cancel HtsUser Reminder" must {

    val ninoNew = "AE123456D"
    val cancelHtsUserReminder = CancelHtsUserReminder(ninoNew)

    "return http response as it is to the caller" in {
      val response =
        HttpResponse(200)
      mockPost(cancelHtsReminderURL, Map.empty, cancelHtsUserReminder)(Some(response))
      val result = connector.cancelHtsUserReminders(cancelHtsUserReminder)
      await(result.value) should equal(Right(()))

    }
  }

  "Update HtsUser Reminder Email" must {

    val ninoNew = "AE123456D"
    val email = "test@user.com"
    val updateReminderEmail = UpdateReminderEmail(ninoNew, email, "Tyrion", "Lannister")

    "return http response as it is to the caller" in {
      val response =
        HttpResponse(200)
      mockPost(emailUpdateHtsReminderURL, Map.empty, updateReminderEmail)(Some(response))
      val result = connector.updateReminderEmail(updateReminderEmail)
      await(result.value) should equal(Right(()))

    }
  }

  private def testCommon[E, A, B](
    mockHttp: ⇒ Option[HttpResponse] ⇒ Unit,
    result: () ⇒ EitherT[Future, E, A],
    validBody: B,
    testInvalidJSON: Boolean = true
  )(
    implicit
    writes: Writes[B]
  ): Unit = { // scalstyle:ignore method.length
    "make a request to the help-to-save backend" in {
      mockHttp(Some(HttpResponse(200)))
      await(result().value)
    }

    "return an error" when {

      if (testInvalidJSON) {
        "the call comes back with a 200 and an unknown JSON format" in {
          mockHttp(
            Some(
              HttpResponse(
                200,
                responseJson = Some(
                  Json.parse(
                    """
                      |{
                      |  "foo": "bar"
                      |}
              """.stripMargin
                  )
                )
              )
            )
          )

          await(result().value).isLeft shouldBe
            true
        }
      }

      "the call comes back with any other status other than 200" in {
        forAll { status: Int ⇒
          whenever(status =!= 200) {
            // check we get an error even though there was valid JSON in the response
            mockHttp(Some(HttpResponse(status, Some(Json.toJson(validBody)))))
            await(result().value).isLeft shouldBe true

          }
        }
      }

      "the future fails" in {
        mockHttp(None)
        await(result().value).isLeft shouldBe true
      }
    }
  }

}
