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

package uk.gov.hmrc.helptosavefrontend.forms

import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration

class EmailValidationSpec extends WordSpec with Matchers with GeneratorDrivenPropertyChecks {

  "EmailValidation" must {

    "validate against the configured regex and max length" in {

      val testRegex = "^[A-Z]{1,100}$"

      val testMaxLength = 50

      val emailValidation = new EmailValidation(Configuration(
        "email-validation.regex" → testRegex,
        "email-validation.max-length" → testMaxLength
      ))

        def test(expectedResult: Boolean)(s: String): Unit = {
          val result = emailValidation.emailMapping.bind(Map("" → s))
          result.isRight shouldBe expectedResult
        }

      val testSuccess: String ⇒ Unit = test(expectedResult = true)
      val testFailure: String ⇒ Unit = test(expectedResult = false)

      // test upper case strings shorter than the maximum length are ok
      forAll(Gen.alphaUpperStr){ s ⇒
        whenever(s.length <= testMaxLength && s.nonEmpty){
          testSuccess(s)
        }
      }

      // test upper case strings less longer than the maximum length are ok
      forAll(Gen.alphaUpperStr){ s ⇒
        whenever(s.size > testMaxLength){
          testFailure(s.toUpperCase())
        }
      }

      // test lower case strings always fail
      forAll(Gen.alphaLowerStr){ s ⇒
        whenever(s.size <= testMaxLength && s.nonEmpty){
          testFailure(s)
        }
      }

    }

  }

}
