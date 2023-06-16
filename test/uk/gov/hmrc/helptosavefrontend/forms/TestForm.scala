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

import play.api.data.Forms.{mapping, text}
import play.api.data.{Form, FormError}

object TestForm {

  case class TestData(s: String)

  val key = "key"

  val testForm: Form[TestData] = Form(
    mapping(
      key -> text
    )(TestData.apply)(TestData.unapply)
  )

  def testFormWithErrorMessage(message: String): Form[TestData] =
    testForm.copy(errors = List(FormError(key, message)))

}
