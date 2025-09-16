/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.views.utils

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.matchers.must.Matchers.mustBe
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.inject.Injector
import play.api.mvc.{AnyContentAsEmpty, MessagesControllerComponents}
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.helptosavefrontend.models.FakeHtsContext

trait ViewBaseSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  implicit lazy val messagesControllerComponents: Lang =
    app.injector.instanceOf[MessagesControllerComponents].langs.availables.head
  implicit val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  implicit lazy val messages: Messages = MessagesImpl(messagesControllerComponents, messagesApi)

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  lazy val injector: Injector = app.injector
  implicit val lang: Lang = Lang("en")
  implicit val context: FakeHtsContext.type = FakeHtsContext

  def asDocument(html: Html): Document = Jsoup.parse(html.toString())

  def messageFromMessageKey(messageKey: String, args: Any*)(implicit messagesApi: MessagesApi): String = {
    val m = messagesApi(messageKey, args *)(using lang)
    if (m === messageKey) sys.error(s"Message key `$messageKey` is missing a message")
    m
  }

  def pageWithExpectedMessages(checks: Seq[(String, String)])(implicit document: Document): Unit = checks.foreach {
    case (cssSelector, message) =>
      s"element with cssSelector '$cssSelector'" must {
        s"have message '$message'" in {
          val elem = document.select(cssSelector)
          elem.first.text() mustBe message
        }
      }
  }
}
