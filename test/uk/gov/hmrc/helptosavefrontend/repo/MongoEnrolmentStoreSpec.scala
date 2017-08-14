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

package uk.gov.hmrc.helptosavefrontend.repo

import play.api.libs.json.Json
import reactivemongo.api.indexes.Index
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.repo.EnrolmentStore.{Enrolled, NotEnrolled, Status}
import uk.gov.hmrc.helptosavefrontend.repo.MongoEnrolmentStore.EnrolmentData
import uk.gov.hmrc.helptosavefrontend.util.NINO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class MongoEnrolmentStoreSpec extends TestSupport with MongoTestSupport[EnrolmentData,MongoEnrolmentStore] {

  def newMongoStore() = new MongoEnrolmentStore(mockMongo) {

      override def indexes: Seq[Index] = {
        // this line is to ensure scoverage picks up this line in MongoEnrolmentStore -
        // we can't really test the indexes function, it doesn't affect the behaviour of
        // the class only its performance
        super.indexes
        Seq.empty[Index]
      }

      override def doUpdate(nino: NINO, itmpFlag: Boolean)(implicit ec: ExecutionContext): Future[Option[EnrolmentData]] =
        mockDBFunctions.update(EnrolmentData(nino, itmpFlag))

      override def find(query: (String, Json.JsValueWrapper)*)(implicit ec: ExecutionContext): Future[List[EnrolmentData]] =
        query.toList match {
          case (_, value) :: Nil ⇒
            mockDBFunctions.get[Json.JsValueWrapper, EnrolmentData](value)

          case _ ⇒ fail("find method called with multiple NINOs")
        }
  }

  "The MongoEnrolmentStore" when {

    val nino = "NINO"

    "updating" must {

      def update(nino: NINO, itmpNeedsUpdate: Boolean): Either[String, Unit] =
        Await.result(mongoStore.update(nino, itmpNeedsUpdate).value, 5.seconds)


      "update the mongodb collection" in {
        mockUpdate(EnrolmentData(nino, true))(Right(Some(EnrolmentData(nino, true))))
        update(nino, true)
      }

      "return successfully if the update was successful" in {
        mockUpdate(EnrolmentData(nino, false))(Right(Some(EnrolmentData(nino, false))))
        update(nino, false) shouldBe Right(())
      }

      "return an error" when {

        "the update result from mongo is negative" in {
          mockUpdate(EnrolmentData(nino, true))(Right(None))
          update(nino, true).isLeft shouldBe true
        }

        "the future returned by mongo fails" in {
          mockUpdate(EnrolmentData(nino, false))(Left("Uh oh!"))
          update(nino, false).isLeft shouldBe true

        }
      }
    }

    "getting" must {

      def get(nino: NINO): Either[String, Status] =
        Await.result(mongoStore.get(nino).value, 5.seconds)

      "attempt to find the entry in the collection based on the input nino" in {
        mockFind(nino)(Future.successful(List.empty[EnrolmentData]))

        get(nino)
      }

      "return an enrolled status if an entry is found" in {
        mockFind(nino)(Future.successful(List(EnrolmentData("a", true), EnrolmentData("b", false))))


        get(nino) shouldBe Right(Enrolled(true))
      }

      "return a not enrolled status if the entry is not found" in {
        mockFind(nino)(Future.successful(List.empty[EnrolmentData]))

        get(nino) shouldBe Right(NotEnrolled)
      }

      "return an error if there is an error while finding the entry" in {
        mockFind(nino)(Future.failed(new Exception))

        get(nino).isLeft shouldBe true
      }
    }

  }
}