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

package uk.gov.hmrc.helptosavefrontend.connectors

import com.google.inject.{ImplementedBy, Singleton}
import play.api.libs.json.{Reads, Writes}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{keyStoreDomain, keyStoreUrl, sessionCacheKey}
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.models.HTSSession
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache}
import uk.gov.hmrc.play.config.{AppName, ServicesConfig}
import uk.gov.hmrc.play.http._

import scala.concurrent.Future

@ImplementedBy(classOf[SessionCacheConnectorImpl])
trait SessionCacheConnector extends SessionCache with ServicesConfig {

  val sessionKey: String

  def put(body: HTSSession)(implicit writes: Writes[HTSSession], hc: HeaderCarrier): Future[CacheMap] =
    cache[HTSSession](sessionKey, body)(writes, hc)

  def get(implicit hc: HeaderCarrier, reads: Reads[HTSSession]): Future[Option[HTSSession]] =
    fetchAndGetEntry[HTSSession](sessionKey)(hc, reads)

}

@Singleton
class SessionCacheConnectorImpl extends SessionCacheConnector with AppName {

  override def defaultSource: String = appName

  val sessionKey: String = sessionCacheKey

  override def baseUri: String = keyStoreUrl

  override def domain: String = keyStoreDomain

  override def http: HttpGet with HttpPut with HttpDelete = WSHttp
}
