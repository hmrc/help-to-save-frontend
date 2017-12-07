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

package hts.steps

import hts.pages.{MissingUserInfoPage, Page}
import hts.utils.Helpers

class MissingUserInfoSteps extends Steps with Page {

  Then("""^they see that their (.+) is missing$""") { (field: String) ⇒
    getCurrentUrl should include(MissingUserInfoPage.url)
    Helpers.isTextOnPage(field) shouldBe true

  }

}
