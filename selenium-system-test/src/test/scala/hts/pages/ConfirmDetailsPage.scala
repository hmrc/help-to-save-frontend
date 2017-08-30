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

import org.openqa.selenium.{By, WebDriver}
import hts.utils.Configuration

object ConfirmDetailsPage extends WebPage {

  def goToPage()(implicit driver: WebDriver): Unit =
    go to s"${Configuration.host}/help-to-save/register/check-and-confirm-your-details"

  def continue()(implicit driver: WebDriver): Unit = click on "continue"

}
