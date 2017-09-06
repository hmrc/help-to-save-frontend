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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.HttpVerbs.{GET ⇒ GET_VERB, POST ⇒ POST_VERB}
import play.api.libs.json.Writes
import play.api.libs.ws.WSProxyServer
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.config.{AuditingConfig, LoadAuditingConfig}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector ⇒ Auditing}
import uk.gov.hmrc.play.config.{AppName, RunMode, ServicesConfig}
import uk.gov.hmrc.play.http.hooks.HttpHook
import uk.gov.hmrc.play.http.ws._
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse, ws}

import scala.concurrent.Future

object FrontendAuditConnector extends Auditing with AppName {
  override lazy val auditingConfig: AuditingConfig = LoadAuditingConfig("auditing")
}

@ImplementedBy(classOf[WSHttpExtension])
trait WSHttp extends WSGet with WSPut with WSPost with WSDelete {

  def get(url: String)(implicit rhc: HeaderCarrier): Future[HttpResponse]

  def post[A](url:     String,
              body:    A,
              headers: Seq[(String, String)] = Seq.empty[(String, String)]
  )(implicit rds: Writes[A], hc: HeaderCarrier): Future[HttpResponse]

}

@Singleton
class WSHttpExtension extends WSHttp {

  override val hooks: Seq[HttpHook] = NoneRequired

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
  def post[A](url:     String,
              body:    A,
              headers: Seq[(String, String)] = Seq.empty[(String, String)]
  )(implicit rds: Writes[A], hc: HeaderCarrier): Future[HttpResponse] = withTracing(POST_VERB, url) {
    val httpResponse = doPost(url, body, headers)
    executeHooks(url, POST_VERB, None, httpResponse)
    httpResponse
  }
}

@Singleton
class FrontendAuthConnector @Inject() (wsHttp: WSHttp) extends PlayAuthConnector with ServicesConfig {
  override lazy val serviceUrl: String = baseUrl("auth")

  override def http = wsHttp
}

class WSHttpProxy extends ws.WSHttp with WSProxy with RunMode with HttpAuditing with ServicesConfig {
  override lazy val appName: String = getString("appName")
  override lazy val wsProxyServer: Option[WSProxyServer] = WSProxyConfiguration("proxy")
  override val hooks: Seq[HttpHook] = Seq(AuditingHook)
  override lazy val auditConnector: Auditing = FrontendAuditConnector

  /**
   * Returns a [[Future[HttpResponse]] without throwing exceptions if the status us not `2xx`. Needed
   * to replace [[POST]] method provided by the hmrc library which will throw exceptions in such cases.
   */
  def post[A](url:     String,
              body:    A,
              headers: Map[String, String] = Map.empty[String, String]
  )(implicit rds: Writes[A], hc: HeaderCarrier): Future[HttpResponse] =
    doPost(url, body, headers.toSeq)
}
