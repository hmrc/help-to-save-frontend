/*
 * Copyright 2019 HM Revenue & Customs
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

package hts.pages.registrationPages

object NotEligibleReason5Page extends NotEligiblePage {

  override val notEligibleText =
    List("This is because your household income - in your last monthly assessment period - was less than £542.88. Your Universal Credit payments are not considered to be income.",
      "If your situation changes, you can apply for a Help to Save account again.")
}
