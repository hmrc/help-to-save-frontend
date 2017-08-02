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

package uk.gov.hmrc.helptosavefrontend.enrolment

import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{JsString, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DefaultDB
import reactivemongo.api.indexes.Index
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.enrolment.EnrolmentStore.{Enrolled, NotEnrolled, Status}
import uk.gov.hmrc.helptosavefrontend.enrolment.MongoEnrolmentStore.EnrolmentData
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.mongo.MongoConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class MongoEnrolmentStoreSpec extends TestSupport{

  trait MockDBFunctions {
    def update[A](a: A): Future[Option[A]]
    def get[A,B](a: A): Future[List[B]]
  }

  val mockDBFunctions = mock[MockDBFunctions]

  val mockMongo = mock[ReactiveMongoComponent]

  val store = {
    val connector = mock[MongoConnector]
    val db = stub[DefaultDB]
    (mockMongo.mongoConnector _).expects().returning(connector)
    (connector.db _).expects().returning(() ⇒ db)

    new MongoEnrolmentStore(mockMongo) {

      override def indexes: Seq[Index] = {
        // this line is to ensure scoverage picks up this line in MongoEnrolmentStore -
        // we can't really test the indexes function, it doesn't affect the behaviour of
        // the class only its performance
        super.indexes
        Seq.empty[Index]
      }

      override def doUpdate(data: EnrolmentData)(implicit ec: ExecutionContext): Future[Option[EnrolmentData]] =
        mockDBFunctions.update[EnrolmentData](data)


      override def find(query: (String, Json.JsValueWrapper)*
                       )(implicit ec: ExecutionContext): Future[List[EnrolmentData]] =
        query.toList match {
          case (_, value) :: Nil ⇒
            mockDBFunctions.get[Json.JsValueWrapper, EnrolmentData](value)

          case _ ⇒ fail("find method called with multiple NINOs")
        }
    }
  }

  def mockInsert(nino: NINO, itmpNeedsUpdate: Boolean)(result: ⇒ Future[Option[EnrolmentData]]): Unit =
    (mockDBFunctions.update[EnrolmentData](_: EnrolmentData))
      .expects(EnrolmentData(nino, itmpNeedsUpdate))
      .returning(result)

  def mockFind(nino: NINO)(result: ⇒ Future[List[(NINO,Boolean)]]): Unit =
    (mockDBFunctions.get[Json.JsValueWrapper, EnrolmentData](_: Json.JsValueWrapper))
      .expects(toJsFieldJsValueWrapper(JsString(nino)))
      .returning(result.map(_.map{ case (n,b) ⇒ EnrolmentData(n,b) }))


  "The MongoEnrolmentStore" when {

    val nino = "NINO"

    "putting" must {

      def put(nino: NINO, itmpNeedsUpdate: Boolean): Either[String,Unit] =
        Await.result(store.update(nino, itmpNeedsUpdate).value, 5.seconds)


      "insert into the mongodb collection" in {
        mockInsert(nino, true)(Future.successful(Some(EnrolmentData(nino, true))))

        put(nino, true)
      }

      "return successfully if the write was successful" in {
        mockInsert(nino, false)(Future.successful(Some(EnrolmentData(nino, false))))

        put(nino, false) shouldBe Right(())
      }

      "return an error" when {

        "the write result from mongo is negative" in {
          mockInsert(nino, true)(Future.successful(None))

          put(nino, true).isLeft shouldBe true
        }

        "the future returned by mongo fails" in {
          mockInsert(nino, false)(Future.failed(new Exception))

          put(nino, false).isLeft shouldBe true

        }

      }
    }

    "getting" must {

      def get(nino: NINO): Either[String,Status] =
        Await.result(store.get(nino).value, 5.seconds)

      "attempt to find the entry in the collection based on the input nino" in {
        mockFind(nino)(Future.successful(List.empty[(NINO,Boolean)]))

        get(nino)
      }

      "return an enrolled status if an entry is found" in {
        mockFind(nino)(Future.successful(List("a" → true, "b" → false)))


        get(nino) shouldBe Right(Enrolled(true))
      }

      "return a not enrolled status if the entry is not found" in {
        mockFind(nino)(Future.successful(List.empty[(NINO,Boolean)]))

        get(nino) shouldBe Right(NotEnrolled)
      }

      "return an error if there is an error while findinng the entry" in {
        mockFind(nino)(Future.failed(new Exception))

        get(nino).isLeft shouldBe true
      }
    }

  }


}