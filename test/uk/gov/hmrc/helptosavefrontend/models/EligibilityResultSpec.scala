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

package uk.gov.hmrc.helptosavefrontend.models

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsPath, JsSuccess, Json}


class EligibilityResultSpec extends WordSpec with Matchers {

  "An EligibilityResult" must {

    val user = randomUserInfo()
    val success = EligibilityResult(Some(user))
    val failure = EligibilityResult(None)

    "have a fold method" in {
      def f(result: EligibilityResult): String = result.fold("Nay", _ ⇒ "Yay")

      f(success) shouldBe "Yay"
      f(failure) shouldBe "Nay"
    }

    "have a JSON format instance" in {
      List(success, failure).foreach { result ⇒
        val json = Json.toJson(result)
        val eligibilityResult = Json.fromJson[EligibilityResult](json)
        eligibilityResult.isSuccess shouldBe true
        eligibilityResult.get shouldBe result
      }
    }

  }


}
