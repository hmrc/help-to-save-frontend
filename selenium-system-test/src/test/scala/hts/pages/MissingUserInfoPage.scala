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

package hts.pages

import hts.utils.Configuration

/**
 * Created by jackie on 01/12/17.
 */
object MissingUserInfoPage extends Page {

  val url: String = s"${Configuration.host}/help-to-save/check-eligibility"

  val expectedTitle: String = "We couldn't retrieve the following details"

  def missingDetails(details: List[String], pageContent: String): Boolean = {
    def go(n: Int) {
      if
      if (pageContent contains (details.take(n))) {
        go(n + 1)
        true
      } else false
    }
    go(1)
  }

}