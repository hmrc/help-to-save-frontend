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

package uk.gov.hmrc.helptosavefrontend.connectors

import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.models.HTSSession.EligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResultType.Ineligible
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.duration._

class SessionCacheConnectorImplSpec extends TestSupport with HttpSupport with ScalaFutures with GeneratorDrivenPropertyChecks {

  implicit val config: PatienceConfig = PatienceConfig().copy(timeout = scaled(10.seconds))

  class TestApparatus {

    implicit val eligibleWithUserInfoGen: Gen[EligibleWithUserInfo] = for {
      eligible ← TestData.Eligibility.randomEligibility()
      userInfo ← TestData.UserData.userInfoGen
    } yield EligibleWithUserInfo(eligible, userInfo)

    implicit val htsSessionGen: Gen[HTSSession] =
      for {
        result ← Gen.option(
          Gen.oneOf[Either[Ineligible, EligibleWithUserInfo]](
            TestData.Eligibility.ineligibilityGen.map(Left(_)),
            eligibleWithUserInfoGen.map(Right(_))))
        email ← Gen.option(Gen.alphaStr)
        pendingEmail ← Gen.option(Gen.alphaStr)
      } yield HTSSession(result, email, pendingEmail)

    implicit val htsSessionArb: Arbitrary[HTSSession] = Arbitrary(htsSessionGen)

    def cacheMap(htsSession: HTSSession) = CacheMap("1", Map("htsSession" -> Json.toJson(htsSession)))

    val sessionCacheConnector = new SessionCacheConnectorImpl(mockHttp, mockMetrics)

    val sessionId = headerCarrier.sessionId.getOrElse(sys.error("Could not find session iD"))

    val putUrl: String = s"http://localhost:8400/keystore/help-to-save-frontend/${sessionId.value}/data/htsSession"
    val getUrl: String = s"http://localhost:8400/keystore/help-to-save-frontend/${sessionId.value}"

  }

  "The SessionCacheConnector" should {

      def mockSessionGet(url: String)(response: CacheMap) =
        mockGet(url)(Some(HttpResponse(200, Some(Json.toJson(response)))))

      def mockSessionPut(url: String, session: HTSSession)(response: CacheMap) =
        mockPut(url, session)(Some(HttpResponse(200, Some(Json.toJson(response)))))

    "be able to insert a new HTSSession into the cache" in new TestApparatus {
      forAll(htsSessionGen) { htsSession ⇒
        val cache = cacheMap(htsSession)

        inSequence {
          mockSessionGet(getUrl)(CacheMap("1", Map.empty[String, JsValue]))
          mockSessionPut(putUrl, htsSession)(cache)
        }

        val result = sessionCacheConnector.put(htsSession)

        result.value.futureValue should be(Right(cache))

      }
    }

    "be able to update an existing HTSSession" in new TestApparatus {
      forAll(htsSessionGen) { htsSession ⇒
        val ivUrl = "/some/iv/url"
        val ivSuccessUrl = "/some/iv/successUrl"

        val existingSession = HTSSession(None, None, None, ivURL = Some(ivUrl), ivSuccessURL = Some(ivSuccessUrl))
        val expectedSessionToStore = htsSession.copy(ivURL        = Some(ivUrl), ivSuccessURL = Some(ivSuccessUrl))
        val cacheAfterPut = cacheMap(expectedSessionToStore)

        inSequence {
          mockSessionGet(getUrl)(cacheMap(existingSession))
          mockSessionPut(putUrl, expectedSessionToStore)(cacheAfterPut)
        }

        val result = sessionCacheConnector.put(expectedSessionToStore)

        result.value.futureValue should be(Right(cacheAfterPut))

      }
    }

    "be able to update IV data" in new TestApparatus {
      val oldSession = HTSSession(None, None, None, ivURL = Some("a"), None)
      val expectedSession = HTSSession(None, None, None, Some("a"), Some("b"))

      inSequence {
        mockSessionGet(getUrl)(cacheMap(oldSession))
        mockSessionPut(putUrl, expectedSession)(cacheMap(expectedSession))
      }

      val result = sessionCacheConnector.put(HTSSession(None, None, None, None, Some("b")))
      result.value.futureValue.isRight should be(true)
    }

    "be able to Get a HTSSession from the cache" in new TestApparatus {
      forAll(htsSessionGen) { htsSession ⇒
        val cache = cacheMap(htsSession)

        mockSessionGet(getUrl)(cache)

        val result = sessionCacheConnector.get
        result.value.futureValue should be(Right(Some(htsSession)))

      }
    }

  }

}
