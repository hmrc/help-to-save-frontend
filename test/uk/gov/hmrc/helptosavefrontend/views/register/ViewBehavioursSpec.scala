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

import org.jsoup.nodes.Document
import play.api.i18n.MessagesApi
import play.api.test.FakeRequest
import uk.gov.hmrc.helptosavefrontend.TestSupport

class ViewBehavioursSpec extends TestSupport {

  lazy val injector = fakeApplication.injector
  lazy val request = FakeRequest()
  def messagesApi = injector.instanceOf[MessagesApi]
  lazy val messages = messagesApi.preferred(request)

  def assertEqualsMessage(doc: Document, cssSelector: String, expectedMessageKey: String) = {
    val elements = doc.select(cssSelector)
    if (elements.isEmpty) sys.error(s"CSS Selector $cssSelector wasn't rendered.")
    //<p> HTML elements are rendered out with a carriage return on some pages, so discount for comparison
    assert(elements.first().html().replace("\n", "") == messagesApi(expectedMessageKey))
  }

  def assertPageTitleEqualsMessage(doc: Document, expectedMessageKey: String, args: Any*) = {
    val headers = doc.getElementsByTag("h1")
    headers.size shouldBe 1
    headers.first.text.replaceAll("\u00a0", " ") shouldBe messages(expectedMessageKey, args: _*).replaceAll("&nbsp;", " ")
  }

  def assertContainsLabel(doc: Document, forElement: String, expectedText: String) = {
    val labels = doc.getElementsByAttributeValue("for", forElement)
    assert(labels.size == 1, s"\n\nLabel for $forElement was not rendered on the page.")
    assert(labels.first.text() == expectedText, s"\n\nLabel for $forElement was not $expectedText")
  }

  def assertButtonText(doc: Document, expectedMessageKey: String) = {
    assert(doc.select("div > button").toString().contains(messages(expectedMessageKey)))
  }

  def assertRenderedById(doc: Document, id: String) = {
    assert(doc.getElementById(id) != null, "\n\nElement " + id + " was not rendered on the page.\n")
  }
}
