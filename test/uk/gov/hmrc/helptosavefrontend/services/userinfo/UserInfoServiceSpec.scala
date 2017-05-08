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
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.helptosavefrontend.connectors.CitizenDetailsConnector
import uk.gov.hmrc.helptosavefrontend.connectors.CitizenDetailsConnector.{Address, CitizenDetailsResponse, Person}
import uk.gov.hmrc.helptosavefrontend.models.UserInfo
import uk.gov.hmrc.helptosavefrontend.services.userinfo.UserInfoService.UserDetailsResponse
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class UserInfoServiceSpec extends UnitSpec with WithFakeApplication with MockFactory {

  implicit val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  val cdResponse = CitizenDetailsResponse(Some(Person(Some("test"), Some("last"), Some(LocalDate.now()))),
    Some(Address(Some("line1"), Some("line2"), Some("line3"), Some("line4"), Some("line5"), Some("POSTCODE"), Some("uk"))))


  /**
    * Contains fresh instances of mocks for each test
    */
  class TestApparatus {
    val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]

    def mockCitizenDetailsConnector(nino: NINO,
                                    citizenDetailsResponse: CitizenDetailsResponse): Unit =
      (mockCitizenDetailsConnector.getDetails(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(EitherT.pure[Future, String, CitizenDetailsResponse](cdResponse))

    val service = new UserInfoService(mockCitizenDetailsConnector)
  }

  "The HTSUserInfoService" when {

    "getting user info" must {

      val userDetailsResponse = UserDetailsResponse("test", Some("last"), Some("test@test.com"), Some(LocalDate.now()))
      val citizenDetailsResponse = cdResponse
      val nino = "WM123456C"
      val userDetailsUri = Some("/test/user/uri")


      "use the auth connector to get user details from the user-details service" in new TestApparatus {
        service.getUserInfo(userDetailsUri, nino)
      }

      "use the citizen details connector to get further user details from the citizen-details service" in new TestApparatus {
        inSequence {
          mockCitizenDetailsConnector(nino, citizenDetailsResponse)
        }

        val result = service.getUserInfo(userDetailsUri, nino)
        Await.result(result.value, 3.seconds)
      }


      "combine the two sources of information to get full user information" in new TestApparatus {
        inSequence {
          mockCitizenDetailsConnector(nino, citizenDetailsResponse)
        }

        // test if user details has all the info needed
        val result = service.getUserInfo(userDetailsUri, nino)
        Await.result(result.value, 3.seconds) shouldBe Right(UserInfo(
          "test last",
          nino,
          userDetailsResponse.dateOfBirth.getOrElse(sys.error("Could not find date of birth")),
          userDetailsResponse.email.getOrElse(sys.error("Could not find email")),
          citizenDetailsResponse.address.map(_.toList()).getOrElse(sys.error("Could not find address"))
        ))

        // test if user details does not have the surname
        inSequence {
          mockCitizenDetailsConnector(nino, citizenDetailsResponse)
        }

        // test if user details does not have the last name
        val result2 = service.getUserInfo(userDetailsUri, nino)
        Await.result(result2.value, 3.seconds) shouldBe Right(UserInfo(
          userDetailsResponse.name + " " + citizenDetailsResponse.person.flatMap(_.lastName).getOrElse("Could not find surname"),
          nino,
          userDetailsResponse.dateOfBirth.getOrElse(sys.error("Could not find date of birth")),
          userDetailsResponse.email.getOrElse(sys.error("Could not find email")),
          citizenDetailsResponse.address.map(_.toList()).getOrElse(sys.error("Could not find address"))
        ))

        // test if user details does not have the date of birth
        inSequence {
          mockCitizenDetailsConnector(nino, citizenDetailsResponse)
        }

        val result3 = service.getUserInfo(userDetailsUri, nino)
        Await.result(result3.value, 3.seconds) shouldBe Right(UserInfo(
          userDetailsResponse.name + " " + userDetailsResponse.lastName.getOrElse("Could not find surname"),
          nino,
          citizenDetailsResponse.person.flatMap(_.dateOfBirth).getOrElse(sys.error("Could not find date of birth")),
          userDetailsResponse.email.getOrElse(sys.error("Could not find email")),
          citizenDetailsResponse.address.map(_.toList()).getOrElse(sys.error("Could not find address"))
        ))
      }

      //      "return an error if some user information is not available" in new TestApparatus {
      //        def test(userDetailsResponse: UserDetailsResponse,
      //                 citizenDetailsResponse: CitizenDetailsResponse): Unit = {
      //          inSequence {
      //            mockCitizenDetailsConnector(nino, citizenDetailsResponse)
      //          }
      //
      //          val result = service.getUserInfo(nino)
      //          Await.result(result.value, 3.seconds).isLeft shouldBe true
      //        }
      //
      //        test(
      //          userDetailsResponse.copy(lastName = None),
      //          citizenDetailsResponse.copy(person = citizenDetailsResponse.person.map(_.copy(lastName = None))))
      //
      //        test(
      //          userDetailsResponse.copy(dateOfBirth = None),
      //          citizenDetailsResponse.copy(person = citizenDetailsResponse.person.map(_.copy(dateOfBirth = None))))
      //
      //        test(
      //          userDetailsResponse.copy(email = None),
      //          citizenDetailsResponse)
      //
      //        test(
      //          userDetailsResponse,
      //          citizenDetailsResponse.copy(address = None))
      //
      //      }
    }
  }
}