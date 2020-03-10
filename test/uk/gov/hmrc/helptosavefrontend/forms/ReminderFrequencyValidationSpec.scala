/*
 * Copyright 2020 HM Revenue & Customs
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
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Logger
import play.api.data.FormError
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.controllers.ControllerSpecWithGuiceApp
import uk.gov.hmrc.helptosavefrontend.forms.ReminderFrequencyValidation.FormOps
import uk.gov.hmrc.helptosavefrontend.forms.ReminderFrequencyValidation.ErrorMessages
import uk.gov.hmrc.helptosavefrontend.forms.TestForm.{testForm, testFormWithErrorMessage}
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

class ReminderFrequencyValidationSpec
    extends WordSpec with Matchers with ScalaCheckDrivenPropertyChecks with ControllerSpecWithGuiceApp {

  "ReminderFrequencyValidation" must {

    lazy val validation = new ReminderFrequencyValidation(
      new FrontendAppConfig(new ServicesConfig(fakeApplication.configuration, injector.instanceOf[RunMode]))
    )

    def test(
      reminderFrequencyValidation: ReminderFrequencyValidation
    )(value: String)(expectedResult: Either[Set[String], Unit], log: Boolean = false): Unit = {
      val result: Either[Seq[FormError], String] =
        reminderFrequencyValidation.reminderFrequencyFormatter.bind("key", Map("key" → value))
      if (log) Logger.error(value + ": " + result.toString)
      result.leftMap(_.toSet) shouldBe expectedResult.bimap(_.map(s ⇒ FormError("key", s)), _ ⇒ value)
    }

    "validate against blank" in {
      val reminderFrequencyValidation = validation

      test(reminderFrequencyValidation)("")(Left(Set(ErrorMessages.reminderFrequencyEmpty)))
    }

    "validate against not blank" in {
      val reminderFrequencyValidation = validation

      test(reminderFrequencyValidation)("1st")(Right(Set(ErrorMessages.reminderFrequencyEmpty))).equals(false)
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
