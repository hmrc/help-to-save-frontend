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

package uk.gov.hmrc.helptosavefrontend.views.register

import org.jsoup.Jsoup
import org.openqa.selenium.By.ByCssSelector
import play.api.i18n.MessagesApi
import play.api.test.FakeRequest
import reactivemongo.api.BSONSerializationPack.Document
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.forms.UpdateEmailForm
import uk.gov.hmrc.helptosavefrontend.models.HtsContext
import uk.gov.hmrc.helptosavefrontend.views.html.register.update_email_address

class UpdateEmailViewSpec extends TestSupport {

  lazy val injector = fakeApplication.injector
  lazy val request = FakeRequest()

  def messagesApi = injector.instanceOf[MessagesApi]
  lazy val messages = messagesApi.preferred(request)

  def assertEqualsMessage(doc: Document, cssSelector: String, expectedValue: String) = {
    val elements = doc.select(cssSelector)
  }

  val mockHtsContext = mock[HtsContext]
  lazy val view = update_email_address("email@gmail.com", UpdateEmailForm.newEmailForm)(mockHtsContext, request, messages)


  "UpdateEmailView" should {
    "when rendered have the correct banner title" in {
      val doc = Jsoup.parse(view.toString())
      val nav = doc.getElementById("proposition-menu")
      val span = nav.children().first()
      span.text shouldBe messagesApi("hts.helpers.header-page")
    }

    "when rendered must display the correct browser title" in {
      val doc = Jsoup.parse(view.toString())
      assertEqualsMessage(doc, "title", "")
    }
  }
}
