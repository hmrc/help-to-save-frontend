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

package uk.gov.hmrc.helptosavefrontend.util


import play.api.data.FormError
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.forms.{UpdateEmail, UpdateEmailForm}

class FormHelpersSpec extends TestSupport {

  val errorKey = "a key"
  val otherErrorKey = "another key"
  val errorMessage = "an error"

  "GetErrorByKey" must {
    "return an empty string when given nothing" in {
      FormHelpers.getErrorByKey[Int](None, errorKey) shouldBe ""
    }

    "return an empty string when given a form with no errors" in {
      FormHelpers.getErrorByKey[UpdateEmail](Some(UpdateEmailForm.verifyEmailForm), errorKey) shouldBe ""
    }

    "return an empty string when given a form with an error for a different key" in {
      val error = FormError(errorKey, errorMessage)
      val formWithErrors = UpdateEmailForm.verifyEmailForm.withError(error)
      FormHelpers.getErrorByKey[UpdateEmail](Some(formWithErrors), otherErrorKey) shouldBe ""
    }

    "return the error when given a form with an error for this key" in {
      val error = FormError(errorKey, errorMessage)
      val formWithErrors = UpdateEmailForm.verifyEmailForm.withError(error)
      FormHelpers.getErrorByKey[UpdateEmail](Some(formWithErrors), errorKey) shouldBe errorMessage
    }
  }
}
