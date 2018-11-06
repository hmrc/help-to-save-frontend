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

package uk.gov.hmrc.helptosavefrontend.repo

import java.util.UUID

import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.models.HTSSession._
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResultType.Ineligible
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId

import scala.concurrent.duration._

class SessionStoreSpec extends TestSupport with MongoSupport with ScalaFutures with GeneratorDrivenPropertyChecks with Eventually {

  implicit val config: PatienceConfig = PatienceConfig().copy(timeout = scaled(1.minute))

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

    val sessionStore = new SessionStoreImpl(reactiveMongoComponent, mockMetrics)
  }

  "The SessionStore" should {

    "be able to insert and read a new HTSSession into mongo" in new TestApparatus {

      forAll(htsSessionGen) { htsSession ⇒

        val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(UUID.randomUUID().toString)))
        val result = sessionStore.store(htsSession)(htsSessionWrites, hc)

        result.value.futureValue should be(Right(()))

        val getResult = sessionStore.get(htsSessionReads, hc)
        getResult.value.futureValue should be(Right(Some(htsSession)))
      }
    }

    "be able to update an existing HTSSession against the same user sessionId" in new TestApparatus {

      forAll(htsSessionGen) { htsSession ⇒
        val ivUrl = "/some/iv/url"
        val ivSuccessUrl = "/some/iv/successUrl"

        val existingSession = HTSSession(None, None, None, ivURL = Some(ivUrl), ivSuccessURL = Some(ivSuccessUrl))
        val expectedSessionToStore = htsSession.copy(ivURL        = None, ivSuccessURL = None)

        val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(UUID.randomUUID().toString)))
        val result1 = sessionStore.store(existingSession)(htsSessionWrites, hc)
        result1.value.futureValue should be(Right(()))

        val result2 = sessionStore.store(expectedSessionToStore)(htsSessionWrites, hc)
        result2.value.futureValue should be(Right(()))

        val getResult = sessionStore.get(htsSessionReads, hc)
        getResult.value.futureValue should be(Right(Some(htsSession.copy(ivURL        = Some(ivUrl), ivSuccessURL = Some(ivSuccessUrl)))))

      }
    }
  }

}
