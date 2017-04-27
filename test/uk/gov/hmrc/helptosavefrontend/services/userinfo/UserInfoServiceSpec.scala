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

package uk.gov.hmrc.helptosavefrontend.services.userinfo

import java.time.LocalDate

import cats.data.EitherT
import cats.instances.future._

import org.scalacheck.{Arbitrary, Gen}
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.helptosavefrontend.connectors.CitizenDetailsConnector
import uk.gov.hmrc.helptosavefrontend.connectors.CitizenDetailsConnector.{Address, CitizenDetailsResponse}
import uk.gov.hmrc.helptosavefrontend.models.UserInfo
import uk.gov.hmrc.helptosavefrontend.services.userinfo.UserInfoService.UserDetailsResponse
import uk.gov.hmrc.helptosavefrontend.services.userinfo.UserInfoServiceSpec._
import uk.gov.hmrc.helptosavefrontend.testutil._
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, Authority, ConfidenceLevel, CredentialStrength}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class UserInfoServiceSpec extends UnitSpec with WithFakeApplication with MockFactory{

  implicit val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  val authContext: AuthContext = AuthContext(
    Authority("", Accounts(), None, None, CredentialStrength.None, ConfidenceLevel.L0, None, None, None, "")
  )

  /**
    * Contains fresh instances of mocks for each test
    */
  class TestApparatus {
    val mockAuthConnector: AuthConnector = mock[AuthConnector]

    val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]


    def mockAuthConnector(authContext: AuthContext,
                          userDetailsResponse: UserDetailsResponse): Unit =
      (mockAuthConnector.getUserDetails[UserDetailsResponse](_: AuthContext)(_: HeaderCarrier, _: HttpReads[UserDetailsResponse]))
        .expects(authContext, *, *)
        .returning(Future.successful(userDetailsResponse))

    def mockCitizenDetailsConnector(nino: NINO,
                                    citizenDetailsResponse: CitizenDetailsResponse): Unit =
      (mockCitizenDetailsConnector.getDetails(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(EitherT.pure[Future,String,CitizenDetailsResponse](citizenDetailsResponse))

    val service = new UserInfoService(mockAuthConnector, mockCitizenDetailsConnector)
  }

  "The HTSUserInfoService" when{

    "getting user info" must {

      val userDetailsResponse = randomUserDetailsResponse()
      val citizenDetailsResponse = randomCitizenDetailsResponse()
      val nino = "MY NINO"


      "use the auth connector to get user details from the user-details service" in new TestApparatus{
        mockAuthConnector(authContext, userDetailsResponse)
        service.getUserInfo(authContext, nino)
      }

      "use the citizen details connector to get further user details from the citizen-details service" in new TestApparatus{
        inSequence {
          mockAuthConnector(authContext, userDetailsResponse)
          mockCitizenDetailsConnector(nino, citizenDetailsResponse)
        }

        val result = service.getUserInfo(authContext, nino)
        Await.result(result.value, 3.seconds)
      }


      "combine the two sources of information to get full user information" in new TestApparatus{
        inSequence{
          mockAuthConnector(authContext, userDetailsResponse)
          mockCitizenDetailsConnector(nino, citizenDetailsResponse)
        }

        val result = service.getUserInfo(authContext, nino)
        Await.result(result.value, 3.seconds) shouldBe Right(UserInfo(
          userDetailsResponse.name + " " + userDetailsResponse.lastName,
          nino,
          userDetailsResponse.dateOfBirth,
          userDetailsResponse.email,
          citizenDetailsResponse.address.toList()
        ))
      }
    }
  }
}


object UserInfoServiceSpec {

  val userDetailsResponseArb: Arbitrary[UserDetailsResponse] = Arbitrary(for{
    name ← Gen.identifier
    lastName ← Gen.identifier
    email ← Gen.alphaNumStr
    dateOfBirth ← Gen.choose(0L,100L).map(LocalDate.ofEpochDay)
  } yield UserDetailsResponse(name, lastName, email, dateOfBirth))

  val addressArb: Arbitrary[Address] = Arbitrary(for{
    line1 ← Gen.identifier
    line2 ← Gen.identifier
    line3 ← Gen.identifier
    line4 ← Gen.identifier
    line5 ← Gen.identifier
    postcode ← Gen.identifier
    country ← Gen.identifier
  } yield Address(line1, line2, line3, line4, line5, postcode, country)
  )

  val citizenDetailsResponseArb: Arbitrary[CitizenDetailsResponse] =
    Arbitrary(addressArb.arbitrary.map(CitizenDetailsResponse.apply))

  def randomUserDetailsResponse(): UserDetailsResponse =
    sample(userDetailsResponseArb)

  def randomCitizenDetailsResponse(): CitizenDetailsResponse =
    sample(citizenDetailsResponseArb)

}