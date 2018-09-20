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

package hts.pages.registrationPages

object NotEligibleReason3Page extends NotEligiblePage {

  override val notEligibleText =
    List("You can only open a Help to Save account if you are entitled to Working Tax Credit and also receiving payments for Working Tax Credit or Child Tax Credit.",
      "You should wait until you’ve received a letter from HM Revenue and Customs confirming that you’re entitled to Working Tax Credit. Once you’ve received this you can apply for a Help to Save account again.")
}
