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

package src.test.scala.hts.pages.registrationPages

import src.test.scala.hts.pages.WebPage
import src.test.scala.hts.utils.Configuration.host

object AboutPage extends WebPage {

  def navigateToAboutPage(): Unit = {
    go to s"$host/help-to-save/apply-for-help-to-save/about-help-to-save"
  }

}
