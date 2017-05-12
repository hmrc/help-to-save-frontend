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
import uk.gov.hmrc.helptosavefrontend.connectors.CitizenDetailsConnector.{CitizenDetailsPerson, CitizenDetailsResponse}
import uk.gov.hmrc.helptosavefrontend.models.{UserInfo, addressArb}
import uk.gov.hmrc.helptosavefrontend.services.UserInfoService
import uk.gov.hmrc.helptosavefrontend.services.UserInfoService.UserDetailsResponse
import uk.gov.hmrc.helptosavefrontend.services.userinfo.UserInfoServiceSpec._
import uk.gov.hmrc.helptosavefrontend.testutil._
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class UserInfoServiceSpec extends UnitSpec with WithFakeApplication with MockFactory {

  implicit val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  /**
    * Contains fresh instances of mocks for each test
    */
  class TestApparatus {

    val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]

    def mockCitizenDetailsConnector(nino: NINO,
                                    citizenDetailsResponse: CitizenDetailsResponse): Unit =
      (mockCitizenDetailsConnector.getDetails(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(EitherT.pure[Future, String, CitizenDetailsResponse](citizenDetailsResponse))

    val service = new UserInfoService(mockCitizenDetailsConnector)
  }

  "The HTSUserInfoService" when {

    "getting user info" must {

      val userDetailsResponse = randomUserDetailsResponse()
      val citizenDetailsResponse = randomCitizenDetailsResponse()
      val nino = "MY NINO"

      "use the auth connector to get user details from the user-details service" in new TestApparatus {
        service.getUserInfo(userDetailsResponse, nino)
      }

      "use the citizen details connector to get further user details from the citizen-details service" in new TestApparatus {
        inSequence {
          mockCitizenDetailsConnector(nino, citizenDetailsResponse)
        }

        val result = service.getUserInfo(userDetailsResponse, nino)
        Await.result(result.value, 3.seconds)
      }


      "combine the two sources of information to get full user information" in new TestApparatus {
        inSequence {
          mockCitizenDetailsConnector(nino, citizenDetailsResponse)
        }

        // test if user details has all the info needed
        val result = service.getUserInfo(userDetailsResponse, nino)
        Await.result(result.value, 3.seconds) shouldBe Right(UserInfo(
          userDetailsResponse.name,
          userDetailsResponse.lastName.getOrElse("Could not find surname"),
          nino,
          userDetailsResponse.dateOfBirth.getOrElse(sys.error("Could not find date of birth")),
          userDetailsResponse.email.getOrElse(sys.error("Could not find email")),
          citizenDetailsResponse.address.getOrElse(sys.error("Could not find address"))
        ))

        // test if user details does not have the surname
        inSequence {
          mockCitizenDetailsConnector(nino, citizenDetailsResponse)
        }

        // test if user details does not have the last name
        val result2 = service.getUserInfo(userDetailsResponse.copy(lastName = None), nino)
        Await.result(result2.value, 3.seconds) shouldBe Right(UserInfo(
          userDetailsResponse.name,
          citizenDetailsResponse.person.flatMap(_.lastName).getOrElse("Could not find surname"),
          nino,
          userDetailsResponse.dateOfBirth.getOrElse(sys.error("Could not find date of birth")),
          userDetailsResponse.email.getOrElse(sys.error("Could not find email")),
          citizenDetailsResponse.address.getOrElse(sys.error("Could not find address"))
        ))

        // test if user details does not have the date of birth
        inSequence {
          mockCitizenDetailsConnector(nino, citizenDetailsResponse)
        }

        val result3 = service.getUserInfo(userDetailsResponse.copy(dateOfBirth = None), nino)
        Await.result(result3.value, 3.seconds) shouldBe Right(UserInfo(
          userDetailsResponse.name,
          userDetailsResponse.lastName.getOrElse("Could not find surname"),
          nino,
          citizenDetailsResponse.person.flatMap(_.dateOfBirth).getOrElse(sys.error("Could not find date of birth")),
          userDetailsResponse.email.getOrElse(sys.error("Could not find email")),
          citizenDetailsResponse.address.getOrElse(sys.error("Could not find address"))
        ))
      }

      "return an error if some user information is not available" in new TestApparatus {
        def test(userDetailsResponse: UserDetailsResponse,
                 citizenDetailsResponse: CitizenDetailsResponse): Unit = {
          inSequence {
            mockCitizenDetailsConnector(nino, citizenDetailsResponse)
          }

          val result = service.getUserInfo(userDetailsResponse, nino)
          Await.result(result.value, 3.seconds).isLeft shouldBe true
        }

        test(
          userDetailsResponse.copy(lastName = None),
          citizenDetailsResponse.copy(person = citizenDetailsResponse.person.map(_.copy(lastName = None))))

        test(
          userDetailsResponse.copy(dateOfBirth = None),
          citizenDetailsResponse.copy(person = citizenDetailsResponse.person.map(_.copy(dateOfBirth = None))))

        test(
          userDetailsResponse.copy(email = None),
          citizenDetailsResponse)

        test(
          userDetailsResponse,
          citizenDetailsResponse.copy(address = None))

      }
    }
  }
}


object UserInfoServiceSpec {

  val dateGen = Gen.choose(0L, 100L).map(LocalDate.ofEpochDay)

  val userDetailsResponseArb: Arbitrary[UserDetailsResponse] = Arbitrary(for {
    name ← Gen.identifier
    lastName ← Gen.identifier
    email ← Gen.alphaNumStr
    dateOfBirth ← dateGen
  } yield UserDetailsResponse(name, Some(lastName), Some(email), Some(dateOfBirth)))


  val personArb: Arbitrary[CitizenDetailsPerson] = Arbitrary(for {
    firstName ← Gen.identifier
    lastName ← Gen.identifier
    dateOfBirth ← dateGen
  } yield CitizenDetailsPerson(Some(firstName), Some(lastName), Some(dateOfBirth)))

  val citizenDetailsResponseArb: Arbitrary[CitizenDetailsResponse] =
    Arbitrary(for {
      person ← personArb.arbitrary
      address ← addressArb.arbitrary
    } yield CitizenDetailsResponse(Some(person), Some(address)))

  def randomUserDetailsResponse(): UserDetailsResponse =
    sample(userDetailsResponseArb)

  def randomCitizenDetailsResponse(): CitizenDetailsResponse =
    sample(citizenDetailsResponseArb)

}