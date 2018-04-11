/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.libs.json.{Json, Writes}
import play.api.libs.ws.WSProxyServer
import uk.gov.hmrc.http.HttpVerbs.{PUT ⇒ PUT_VERB}
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.ws._

import scala.concurrent.{ExecutionContext, Future}

class RawHttpReads extends HttpReads[HttpResponse] {
  override def read(method: String, url: String, response: HttpResponse): HttpResponse = response
}

@ImplementedBy(classOf[WSHttpExtension])
trait WSHttp
  extends HttpGet with WSGet
  with HttpPost with WSPost
  with HttpPut with WSPut
  with HttpDelete with WSDelete {

  def get(url: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

  def post[A](url:     String,
              body:    A,
              headers: Seq[(String, String)] = Seq.empty[(String, String)]
  )(implicit w: Writes[A], hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

  def put[A](url:     String,
             body:    A,
             headers: Seq[(String, String)] = Seq.empty[(String, String)]
  )(implicit w: Writes[A], hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]
}

@Singleton
class WSHttpExtension @Inject() (override val auditConnector: AuditConnector, config: FrontendAppConfig) extends WSHttp with HttpAuditing {

  val httpReads: HttpReads[HttpResponse] = new RawHttpReads

  override val hooks: Seq[HttpHook] = NoneRequired

  override def appName: String = config.runModeConfiguration.underlying.getString("appName")

  override def mapErrors(httpMethod: String, url: String, f: Future[HttpResponse])(implicit ec: ExecutionContext): Future[HttpResponse] = f

  /**
   * Returns a [[Future[HttpResponse]] without throwing exceptions if the status us not `2xx`. Needed
   * to replace [[GET]] method provided by the hmrc library which will throw exceptions in such cases.
   */
  def get(url: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = super.GET(url)(httpReads, hc, ec)

  /**
   * Returns a [[Future[HttpResponse]] without throwing exceptions if the status us not `2xx`. Needed
   * to replace [[POST]] method provided by the hmrc library which will throw exceptions in such cases.
   */
  def post[A](url:     String,
              body:    A,
              headers: Seq[(String, String)] = Seq.empty[(String, String)]
  )(implicit w: Writes[A], hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = super.POST(url, body)(w, httpReads, hc, ec)

  def put[A](url:     String,
             body:    A,
             headers: Seq[(String, String)] = Seq.empty[(String, String)]
  )(implicit w: Writes[A], hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = super.PUT(url, body)(w, httpReads, hc, ec)

}

class WSHttpProxy @Inject() (override val auditConnector: AuditConnector, config: FrontendAppConfig)
  extends WSPut
  with HttpPut
  with WSPost
  with HttpPost
  with HttpAuditing
  with WSProxy
  with HttpVerb {

  val httpReads: HttpReads[HttpResponse] = new RawHttpReads

  override def appName: String = config.runModeConfiguration.underlying.getString("appName")

  override lazy val wsProxyServer: Option[WSProxyServer] = WSProxyConfiguration("proxy")
  override val hooks: Seq[HttpHook] = Seq(AuditingHook)

  override def mapErrors(httpMethod: String, url: String, f: Future[HttpResponse])(implicit ec: ExecutionContext): Future[HttpResponse] = f

  /**
   * Returns a [[Future[HttpResponse]] without throwing exceptions if the status us not `2xx`. Needed
   * to replace [[POST]] method provided by the hmrc library which will throw exceptions in such cases.
   */
  def post[A](url:     String,
              body:    A,
              headers: Map[String, String] = Map.empty[String, String]
  )(implicit w: Writes[A], hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    super.POST(url, body)(w, httpReads, hc.withExtraHeaders(headers.toSeq: _*), ec)

  /**
   * Returns a [[Future[HttpResponse]] without throwing exceptions if the status us not `2xx`. Needed
   * to replace [[PUT]] method provided by the hmrc library which will throw exceptions in such cases.
   */
  def put[A](url:           String,
             body:          A,
             needsAuditing: Boolean             = true,
             headers:       Map[String, String] = Map.empty[String, String]
  )(implicit w: Writes[A], hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    withTracing(PUT_VERB, url) {
      val httpResponse = doPut(url, body)(w, hc.withExtraHeaders(headers.toSeq: _*))
      if (needsAuditing) {
        executeHooks(url, PUT_VERB, Option(Json.stringify(w.writes(body))), httpResponse)
      }
      mapErrors(PUT_VERB, url, httpResponse).map(response ⇒ httpReads.read(PUT_VERB, url, response))
    }
  }
}
