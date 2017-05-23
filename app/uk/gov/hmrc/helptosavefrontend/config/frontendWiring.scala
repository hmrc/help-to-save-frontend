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

package uk.gov.hmrc.helptosavefrontend.config

import play.api.http.HttpVerbs.{GET ⇒ GET_VERB, POST ⇒ POST_VERB}
import play.api.libs.json.Writes
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.audit.http.config.LoadAuditingConfig
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector ⇒ Auditing}
import uk.gov.hmrc.play.config.{AppName, RunMode, ServicesConfig}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.http.ws.{WSDelete, WSGet, WSPost, WSPut}

import scala.concurrent.Future

object FrontendAuditConnector extends Auditing with AppName {
  override lazy val auditingConfig = LoadAuditingConfig(s"auditing")
}

trait WSHttpExtension extends WSGet with WSPost {

  /**
    * Returns a [[Future[HttpResponse]] without throwing exceptions if the status us not `2xx`. Needed
    * to replace [[GET]] method provided by the hmrc library which will throw exceptions in such cases.
    */
  def get(url: String)(implicit rhc: HeaderCarrier): Future[HttpResponse] = withTracing(GET_VERB, url) {
    val httpResponse = doGet(url)
    executeHooks(url, GET_VERB, None, httpResponse)
    httpResponse
  }

  /**
    * Returns a [[Future[HttpResponse]] without throwing exceptions if the status us not `2xx`. Needed
    * to replace [[POST]] method provided by the hmrc library which will throw exceptions in such cases.
    */
  def post[A](url: String,
              body: A,
              headers: Seq[(String, String)] = Seq.empty[(String, String)]
             )(implicit rds: Writes[A], hc: HeaderCarrier): Future[HttpResponse] = withTracing(POST_VERB, url) {
    val httpResponse = doPost(url, body, headers)
    executeHooks(url, POST_VERB, None, httpResponse)
    httpResponse
  }
}

object WSHttp extends WSGet with WSPut with WSPost with WSDelete with AppName with RunMode with WSHttpExtension {

  override val hooks = NoneRequired

}

object FrontendAuthConnector extends PlayAuthConnector with ServicesConfig {
  override val serviceUrl: String = baseUrl("auth")

  override def http = WSHttp
}
