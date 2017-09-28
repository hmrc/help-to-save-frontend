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

import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.concurrent.ExecutionContext

class SessionCacheConnectorImplSpec extends TestSupport with ScalaFutures {

  class TestApparatus {
    val mockWsHttp: WSHttp = mock[WSHttp]

    val htsSession = HTSSession(Some(validNSIUserInfo), Some("hello"))

    val cacheMap = CacheMap("1", Map("htsSession" -> Json.toJson(htsSession)))

    val sessionCacheConnector = new SessionCacheConnectorImpl(mockWsHttp, mockMetrics)

    val sessionId = headerCarrier.sessionId.getOrElse(sys.error("Could not find session iD"))

    val putUrl: String = s"http://localhost:8400/keystore/help-to-save-frontend/${sessionId.value}/data/htsSession"
    val getUrl: String = s"http://localhost:8400/keystore/help-to-save-frontend/${sessionId.value}"
  }

  "The SessionCacheConnector" should {

    "be able to insert a HTSSession into the cache" in new TestApparatus {

      (mockWsHttp.PUT[HTSSession, CacheMap](_: String, _: HTSSession)(_: Writes[HTSSession], _: HttpReads[CacheMap], _: HeaderCarrier, _: ExecutionContext))
        .expects(putUrl, htsSession, *, *, *, *)
        .returning(cacheMap)

      val result = sessionCacheConnector.put(htsSession)

      result.value.futureValue should be(Right(cacheMap))

    }

    "be able to Get a HTSSession from the cache" in new TestApparatus {

      (mockWsHttp.GET[CacheMap](_: String)(_: HttpReads[CacheMap], _: HeaderCarrier, _: ExecutionContext))
        .expects(getUrl, *, *, *)
        .returning(cacheMap)

      val result = sessionCacheConnector.get

      result.value.futureValue should be(Right(Some(htsSession)))

    }

  }

}
