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

package uk.gov.hmrc.helptosavefrontend.services

import cats.data.EitherT
import cats.instances.future._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.connectors.ITMPConnector
import uk.gov.hmrc.helptosavefrontend.repo.EnrolmentStore
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EnrolmentServiceImplSpec extends TestSupport with GeneratorDrivenPropertyChecks {

  val enrolmentStore: EnrolmentStore = mock[EnrolmentStore]
  val itmpConnector: ITMPConnector = mock[ITMPConnector]

  def mockEnrolmentStoreCreate(nino: NINO, itmpFlag: Boolean, email: String)(result: Either[String, Unit]): Unit =
    (enrolmentStore.create(_: NINO, _: Boolean, _: String))
      .expects(nino, itmpFlag, email)
      .returning(EitherT.fromEither[Future](result))

  def mockEnrolmentStoreUpdate(nino: NINO, itmpFlag: Boolean)(result: Either[String, Unit]): Unit =
    (enrolmentStore.update(_: NINO, _: Boolean))
      .expects(nino, itmpFlag)
      .returning(EitherT.fromEither[Future](result))

  def mockEnrolmentStoreGet(nino: NINO)(result: Either[String, EnrolmentStore.Status]): Unit =
    (enrolmentStore.get(_: NINO))
      .expects(nino)
      .returning(EitherT.fromEither[Future](result))

  def mockITMPConnector(nino: NINO)(result: Either[String, Unit]): Unit =
    (itmpConnector.setFlag(_: NINO)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromEither[Future](result))

  implicit val arbEnrolmentStatus: Arbitrary[EnrolmentStore.Status] =
    Arbitrary(Gen.oneOf(
      Gen.const(EnrolmentStore.NotEnrolled),
      Gen.oneOf(true, false).map(EnrolmentStore.Enrolled)
    ))

  "The EnrolmentServiceImpl" when {

    val service = new EnrolmentServiceImpl(enrolmentStore, itmpConnector)
    val nino = "AE123456C"
    val email = "user@test.com"

    "enrolling a user" must {

      "create a mongo record with the ITMP flag set to false" in {
        mockEnrolmentStoreCreate(nino, itmpFlag = false, email)(Left(""))

        await(service.enrolUser(nino, email).value)
      }

      "set the ITMP flag" in {
        inSequence {
          mockEnrolmentStoreCreate(nino, itmpFlag = false, email)(Right(()))
          mockITMPConnector(nino)(Left(""))
        }

        await(service.enrolUser(nino, email).value)
      }

      "update the mongo record with the ITMP flag set to true" in {
        inSequence {
          mockEnrolmentStoreCreate(nino, itmpFlag = false, email)(Right(()))
          mockITMPConnector(nino)(Right(()))
          mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Right(()))
        }

        await(service.enrolUser(nino, email).value)
      }

      "return a Right if all the steps were successful" in {
        inSequence {
          mockEnrolmentStoreCreate(nino, itmpFlag = false, email)(Right(()))
          mockITMPConnector(nino)(Right(()))
          mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Right(()))
        }

        await(service.enrolUser(nino, email).value) shouldBe Right(())
      }

      "return a Left if any of the steps failed" in {
        inSequence {
          mockEnrolmentStoreCreate(nino, itmpFlag = false, email)(Right(()))
          mockITMPConnector(nino)(Right(()))
          mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Left(""))
        }

        await(service.enrolUser(nino, email).value).isLeft shouldBe true
      }


    }

    "setting the ITMP flag" must {

      "set the ITMP flag" in {
        mockITMPConnector(nino)(Left(""))

        await(service.setITMPFlag(nino).value)
      }

      "update the mongo record with the ITMP flag set to true" in {
        inSequence {
          mockITMPConnector(nino)(Right(()))
          mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Left(""))
        }

        await(service.setITMPFlag(nino).value)
      }

      "return a Right if all the steps were successful" in {
        inSequence {
          mockITMPConnector(nino)(Right(()))
          mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Right(()))
        }

        await(service.setITMPFlag(nino).value) shouldBe Right(())
      }

      "return a Left if any of the steps failed" in {
        def test(mockActions: ⇒ Unit): Unit = {
          mockActions
          await(service.setITMPFlag(nino).value).isLeft shouldBe true
        }

        test(mockITMPConnector(nino)(Left("")))

        test(inSequence {
          mockITMPConnector(nino)(Right(()))
          mockEnrolmentStoreUpdate(nino, itmpFlag = true)(Left(""))
        })
      }

    }

    "getting the user enrolment status" must {

      "get the enrolment status form the enrolment store" in {
        mockEnrolmentStoreGet(nino)(Left(""))

        await(service.getUserEnrolmentStatus(nino).value)
      }

      "return the enrolment status if the call was successful" in {
        forAll { status: EnrolmentStore.Status ⇒
          mockEnrolmentStoreGet(nino)(Right(status))

          await(service.getUserEnrolmentStatus(nino).value) shouldBe Right(status)
        }
      }

      "return an error if the call was not successful" in {
        mockEnrolmentStoreGet(nino)(Left(""))

        await(service.getUserEnrolmentStatus(nino).value).isLeft shouldBe true

      }
    }
  }
}
