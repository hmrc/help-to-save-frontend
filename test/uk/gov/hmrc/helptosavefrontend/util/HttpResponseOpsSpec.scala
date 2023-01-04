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

package uk.gov.hmrc.helptosavefrontend.util

import play.api.libs.json.{Format, JsValue, Json}
import uk.gov.hmrc.http.HttpResponse

class HttpResponseOpsSpec extends UnitSpec {

  case class Test1(a: Int)

  case class Test2(b: String)

  implicit val test1Format: Format[Test1] = Json.format[Test1]
  implicit val test2Format: Format[Test2] = Json.format[Test2]

  case class ThrowingHttpResponse() extends HttpResponse {
    override def allHeaders: Map[String, Seq[String]] = Map.empty

    override def status: Int = 0

    override def json: JsValue = sys.error("Oh no!")

    override def body: String = ""
  }

  "HttpResponseOps" must {

    "provide a method to parse JSON" in {
      import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._

      val status = 200
      val emptyBody= ""
      val emptyHeaders :Map[String, Seq[String]] = Map.empty
      val data = Test1(0)

      // test when there is an exception
      ThrowingHttpResponse().parseJSON[Test1]().isLeft shouldBe true

      // test when there is no JSON
      HttpResponse(status,emptyBody).parseJSON[Test1]().isLeft shouldBe true

      // test when the JSON isn't the right format
      HttpResponse(status, Json.toJson(data),emptyHeaders)
        .parseJSON[Test2]()
        .isLeft shouldBe true

      // test when everything is ok
      HttpResponse(status, Json.toJson(data), emptyHeaders)
        .parseJSON[Test1]() shouldBe Right(data)
    }
  }
}
