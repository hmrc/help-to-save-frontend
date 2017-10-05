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
import uk.gov.hmrc.helptosavefrontend.models.VerifyEmailError.AlreadyVerified
import uk.gov.hmrc.helptosavefrontend.models.{HtsContext, VerifyEmailError}
import uk.gov.hmrc.helptosavefrontend.views.html.email.email_verify_error

class EmailVerifyErrorSpec extends ViewBehavioursSpec {

  def view(messageKey: VerifyEmailError) = email_verify_error(messageKey)(mockHtsContext, request, messages)
  def document(messageKey: VerifyEmailError) = Jsoup.parse(view(messageKey).toString())

  "CheckYourEmail" should {
    "when rendered have the correct banner title" in {
      val nav = document(AlreadyVerified).getElementById("proposition-menu")
      val span = nav.children().first()
      span.text shouldBe messagesApi("hts.helpers.header-page")
    }

    "when rendered must display the correct browser title" in {
      assertEqualsMessage(document(AlreadyVerified), "title",
        "hts.introduction.title")
    }

    "when rendered must have the correct page title" in {
      assertPageTitleEqualsMessage(document(AlreadyVerified),
        "hts.email-verification.error.title")
    }

    "when rendered must display user content" in {
      assertEqualsMessage(document(AlreadyVerified), "p.content",
        "hts.email-verification.error.already-verified.content")
    }
  }
}
