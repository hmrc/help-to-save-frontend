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

package uk.gov.hmrc.helptosavefrontend.config

import akka.stream.Materializer
import akka.util.Timeout
import org.scalatest.{Matchers, WordSpec}
import play.api.Application
import play.api.mvc.Results.Ok
import play.api.mvc.{Action, Headers}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, cookies}
import uk.gov.hmrc.helptosavefrontend.config.SessionFilter
import uk.gov.hmrc.play.test.WithFakeApplication
import scala.concurrent.duration._

class SessionFilterSpec extends WordSpec with WithFakeApplication with Matchers {

  implicit val app: Application = fakeApplication

  implicit val mat: Materializer = fakeApplication.materializer

  implicit val timeout: Timeout = Timeout(5.seconds)

  val noSessionText = "No Session found"
  val filter = new SessionFilter(Ok(noSessionText))
  val fakeRequest = FakeRequest("GET", "/")

  val action = Action {
    Ok("ok")
  }

  "The SessionFilter" when {

    "it receives a request with no session ID" must {

      "perfom the given action and add a cookie to it with a new session ID" in {
        val future = filter(action)(fakeRequest).run
        contentAsString(future) shouldBe noSessionText
        cookies(future).exists(_.name == "htsSession") shouldBe true
      }

    }

    "it receives a request with a session ID" must {

      "do nothing" in {
        val future = filter(action)(fakeRequest.copy(headers = Headers("Cookie" → "htsSession=12345"))).run
        contentAsString(future) shouldBe "ok"
      }
    }

  }


}
