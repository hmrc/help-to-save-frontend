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

package uk.gov.hmrc.helptosavefrontend.connectors

import com.google.inject.Inject
import com.typesafe.config.Config
import play.api.Configuration
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import configs.syntax._
import uk.gov.hmrc.helptosavefrontend.util.NINO

import scala.concurrent.{ExecutionContext, Future}

class TestConnector @Inject()(http: HttpClient, config: Configuration)(implicit ec: ExecutionContext) {

  val conf: Config = config.underlying.get[Config]("microservice.services.help-to-save-reminder").value

  def getHTSUrl(serviceName: String): String = (for {
    proto <- conf.get[String]("protocol")
    host  <- conf.get[String]("host")
    port  <- conf.get[Int]("port")
  } yield s"$proto://$host:$port/$serviceName").value

  val htsReminderUrl: String = getHTSUrl("help-to-save-reminder")

  def getHtsUser(nino: NINO)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    http.GET[HttpResponse](s"$htsReminderUrl/test-only/gethtsuser/$nino")

  def populateReminders(noUsers: Int, emailPrefix: String, daysToReceive: List[Int])(implicit hc: HeaderCarrier): Future[HttpResponse] =
    http.GET[HttpResponse](s"$htsReminderUrl/test-only/populate-reminders/$noUsers/$emailPrefix/$daysToReceive")
}
