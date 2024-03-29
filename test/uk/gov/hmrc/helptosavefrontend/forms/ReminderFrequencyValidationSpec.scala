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

package uk.gov.hmrc.helptosavefrontend.forms

import cats.syntax.either._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Logger
import play.api.data.FormError
import uk.gov.hmrc.helptosavefrontend.controllers.ControllerSpecWithGuiceApp
import uk.gov.hmrc.helptosavefrontend.forms.ReminderFrequencyValidation.{ErrorMessages, FormOps}
import uk.gov.hmrc.helptosavefrontend.forms.TestForm.{testForm, testFormWithErrorMessage}

class ReminderFrequencyValidationSpec extends AnyWordSpec with Matchers with ControllerSpecWithGuiceApp {

  val logger: Logger = Logger(this.getClass)

  "ReminderFrequencyValidation" must {

    lazy val validation = new ReminderFrequencyValidation()

    def test(
      reminderFrequencyValidation: ReminderFrequencyValidation
    )(value: String)(expectedResult: Either[Set[String], Unit], log: Boolean = false): Unit = {
      val result: Either[Seq[FormError], String] =
        reminderFrequencyValidation.reminderFrequencyFormatter.bind("key", Map("key" -> value))
      if (log) logger.error(value + ": " + result.toString)
      result.leftMap(_.toSet) shouldBe expectedResult.bimap(_.map(s => FormError("key", s)), _ => value)
    }

    "validate against blank" in {
      val reminderFrequencyValidation = validation

      test(reminderFrequencyValidation)("")(Left(Set(ErrorMessages.reminderFrequencyEmpty)))
    }

    "validate against not blank" in {
      val reminderFrequencyValidation = validation

      test(reminderFrequencyValidation)("1st")(Right(Set(ErrorMessages.reminderFrequencyEmpty)))
    }

    "informs if the frequency choice is empty" in {
      testForm.frequencyChoiceEmpty("") shouldBe false

      testFormWithErrorMessage("error").frequencyChoiceEmpty(TestForm.key) shouldBe false

    }

    "informs if the frequency choice is not empty" in {
      testForm.frequencyChoiceEmpty("1st") shouldBe false

      testFormWithErrorMessage("").frequencyChoiceEmpty(TestForm.key) shouldBe false

    }
  }

}
