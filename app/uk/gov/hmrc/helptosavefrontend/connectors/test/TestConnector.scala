/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.connectors.test

import com.google.inject.Inject
import com.typesafe.config.Config
import configs.syntax._
import play.api.Configuration
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

class TestConnector @Inject()(http: HttpClient, config: Configuration)(implicit ec: ExecutionContext) {

  val conf: Config = config.underlying.get[Config]("microservice.services.help-to-save-reminder").value
  val htsReminderUrl: String = "http://localhost:7008/help-to-save-reminder"

  def getHtsUser(nino: NINO)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    http.GET[HttpResponse](s"$htsReminderUrl/test-only/gethtsuser/$nino")

  def populateReminders(noUsers: Int, emailPrefix: String, daysToReceive: List[Int])(implicit hc: HeaderCarrier): Future[HttpResponse] = {

    @tailrec
    def makeDaysPath(daysToReceive: List[Int], currentString: String = "daysToReceive?"): String = {
          daysToReceive match {
            case Nil => currentString
            case head :: remainingDays if remainingDays.isEmpty => {
              val daysReadyForUrl = s"$currentString" + "day=" + head.toString
              makeDaysPath(remainingDays, daysReadyForUrl)
            }
            case head :: remainingDays =>
              val daysReadyForUrl = s"$currentString" + "day=" + head.toString + "&"
              makeDaysPath(remainingDays, daysReadyForUrl)
          }
        }

    val as = makeDaysPath(daysToReceive)
    http.GET[HttpResponse](s"$htsReminderUrl/test-only/populate-reminders/$noUsers/$emailPrefix/$as")
  }
}
