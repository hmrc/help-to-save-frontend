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

package uk.gov.hmrc.helptosavefrontend.services

import java.util.UUID

import org.scalamock.handlers.CallHandler4
import play.api.libs.json.Json
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.connectors.BarsConnector
import uk.gov.hmrc.helptosavefrontend.forms.{BankDetails, SortCode}
import uk.gov.hmrc.helptosavefrontend.util.MockPagerDuty
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class BarsServiceSpec extends UnitSpec with TestSupport with MockPagerDuty {

  private val mockBarsConnector: BarsConnector = mock[BarsConnector]

  def mockBarsConnector(bankDetails: BankDetails)(response: Option[HttpResponse]): CallHandler4[BankDetails, UUID, HeaderCarrier, ExecutionContext, Future[HttpResponse]] =
    (mockBarsConnector.validate(_: BankDetails, _: UUID)(_: HeaderCarrier, _: ExecutionContext)).expects(bankDetails, *, *, *)
      .returning(response.fold[Future[HttpResponse]](Future.failed(new Exception("")))(r ⇒ Future.successful(r)))

  val service = new BarsServiceImpl(mockBarsConnector, mockMetrics, mockPagerDuty)

  "The BarsService" when {

    "validating bank details" must {

      val bankDetails = BankDetails(SortCode(1, 2, 3, 4, 5, 6), "accountNumber", None, "accountName")

      val response =
        """{
          |  "accountNumberWithSortCodeIsValid": true,
          |  "nonStandardAccountDetailsRequiredForBacs": "no",
          |  "sortCodeIsPresentOnEISCD":"yes",
          |  "supportsBACS":"yes",
          |  "ddiVoucherFlag":"no",
          |  "directDebitsDisallowed": "yes",
          |  "directDebitInstructionsDisallowed": "yes"
          |}""".stripMargin

      "handle success response from bars as expected" in {
        mockBarsConnector(bankDetails)(Some(HttpResponse(200, Some(Json.parse(response)))))
        val result = await(service.validate(bankDetails))
        result shouldBe Right(true)
      }

      "handle 200 response but missing json field (accountNumberWithSortCodeIsValid)" in {
        val response =
          """{
            |  "nonStandardAccountDetailsRequiredForBacs": "no",
            |  "sortCodeIsPresentOnEISCD":"yes",
            |  "supportsBACS":"yes",
            |  "ddiVoucherFlag":"no",
            |  "directDebitsDisallowed": "yes",
            |  "directDebitInstructionsDisallowed": "yes"
            |}""".stripMargin

        mockBarsConnector(bankDetails)(Some(HttpResponse(200, Some(Json.parse(response)))))
        mockPagerDutyAlert("error parsing the response json from bars check")
        val result = await(service.validate(bankDetails))
        result shouldBe Left("error parsing the response json from bars check")
      }

      "handle unsuccessful response from bars check" in {
        mockBarsConnector(bankDetails)(Some(HttpResponse(400)))
        mockPagerDutyAlert("unexpected status from bars check")
        val result = await(service.validate(bankDetails))
        result shouldBe Left("unexpected status from bars check")
      }

      "recover from unexpected errors" in {
        mockBarsConnector(bankDetails)(None)
        mockPagerDutyAlert("unexpected error from bars check")
        val result = await(service.validate(bankDetails))
        result shouldBe Left("unexpected error from bars check")
      }

    }

  }
}
