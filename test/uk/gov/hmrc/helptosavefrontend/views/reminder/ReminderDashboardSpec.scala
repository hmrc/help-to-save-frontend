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

package uk.gov.hmrc.helptosavefrontend.views.reminder

import org.jsoup.nodes.Document
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.helptosavefrontend.views.html.reminder.reminder_dashboard
import uk.gov.hmrc.helptosavefrontend.views.utils.ViewBaseSpec

class ReminderDashboardSpec extends ViewBaseSpec {
  val reminder_dashboard: reminder_dashboard = injector.instanceOf[reminder_dashboard]
  def applyView(): HtmlFormat.Appendable =
    reminder_dashboard(email = "email@test.com", period = "date", backLink = Some("back"))

  implicit val doc: Document = asDocument(applyView())

  behave like pageWithExpectedMessages(
    Seq(
      (
        "h1",
        messageFromMessageKey("hts.reminder-dashboard.title.h1")
      )
    )
  )
}
