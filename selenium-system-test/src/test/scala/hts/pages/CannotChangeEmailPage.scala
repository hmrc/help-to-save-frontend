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

package hts.pages

import hts.utils.Configuration

object CannotChangeEmailPage extends Page {

  override val expectedURL: String = s"${Configuration.host}/help-to-save/cannot-change-email"

  override val expectedPageHeader: Option[String] = Some("What do you want to do?")

  override val expectedPageTitle: Option[String] = Some("Something went wrong")
}
