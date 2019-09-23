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

package hts.pages.govPages

import hts.pages.BasePage

object GovUKEligibilityPage extends BasePage {

  override val expectedURL: String = "https://www.gov.uk/get-help-savings-low-income/eligibility"

  override val expectedPageHeader: Option[String] = Some("Get help with savings if you’re on a low income (Help to Save)")

  override val expectedPageTitle: Option[String] = Some("Get help with savings if you’re on a low income (Help to Save): Eligibility")
}
