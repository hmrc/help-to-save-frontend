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
import uk.gov.hmrc.helptosavefrontend.forms.UpdateEmailForm
import uk.gov.hmrc.helptosavefrontend.models.HtsContext
import uk.gov.hmrc.helptosavefrontend.views.html.register.update_email_address

class UpdateEmailViewSpec extends ViewBehavioursSpec {

  val mockHtsContext = mock[HtsContext]
  lazy val view = update_email_address("email@gmail.com", Some(UpdateEmailForm.verifyEmailForm))(mockHtsContext, request, messages)
  lazy val document = Jsoup.parse(view.toString())


  "UpdateEmailView" should {
    "when rendered have the correct banner title" in {
      val nav = document.getElementById("proposition-menu")
      val span = nav.children().first()
      span.text shouldBe messagesApi("hts.helpers.header-page")
    }

    "when rendered must display the correct browser title" in {
      assertEqualsMessage(document, "title", "hts.introduction.title")
    }

    "when rendered must have the correct page title" in {
      assertPageTitleEqualsMessage(document, "hts.email-verification.title")
    }

    "contain a label for the value" in {
      assertContainsLabel(document, "value", messages("hts.email-verification.input.label"))
    }

    "contain an input for the value" in {
      assertRenderedById(document, "value")
    }

    "contains the correct text on the submit button" in {
      assertButtonText(document, "hts.email-verification.submit.text")
    }
  }
}
