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

package uk.gov.hmrc.helptosavefrontend.connectors

import java.util.UUID

import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.forms.{BankDetails, SortCode}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class BarsConnectorSpec extends UnitSpec with TestSupport with HttpSupport {

  val connector = new BarsConnectorImpl(mockHttp)

  "The BarsConnector" when {

    "validating bank details" must {

      "set headers and request body as expected and return http response to the caller" in {
        val trackingId = UUID.randomUUID()
        val headers = Map("User-Agent" -> "help-to-save-frontend", "Content-Type" -> "application/json", "X-Tracking-Id" -> trackingId.toString)
        val body = Json.parse(
          """{
             | "account": {
             |    "sortCode": "123456",
             |    "accountNumber": "accountNumber"
             |  }
             |}""".stripMargin
        )

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

        mockPost("http://localhost:9871/validateBankDetails", headers, body)(Some(HttpResponse(Status.OK, Some(Json.parse(response)))))
        val result = await(connector.validate(BankDetails(SortCode(1, 2, 3, 4, 5, 6), "accountNumber", Some("rollNo"), "accountName"), trackingId))

        result.status shouldBe 200
        result.json shouldBe Json.parse(response)
      }
    }
  }

}
