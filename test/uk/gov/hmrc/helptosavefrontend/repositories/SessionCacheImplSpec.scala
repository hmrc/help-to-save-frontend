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

package uk.gov.hmrc.helptosavefrontend.repositories

import com.typesafe.config.ConfigException
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.{DefaultDB, ReadPreference}
import reactivemongo.core.commands.LastError
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.models.{HTSSession, NSIUserInfo, randomUserInfo}
import uk.gov.hmrc.mongo.{DatabaseUpdate, MongoConnector, Saved}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.SessionId

import scala.concurrent.{ExecutionContext, Future}

class SessionCacheImplSpec extends TestSupport {

  trait MockDBFunctions {
    def update(id: Id, key: String, data: JsValue): Future[DatabaseUpdate[Cache]]

    def get(id: Id, readPreference: ReadPreference): Future[Option[Cache]]
  }

  val mockDBFunctions: MockDBFunctions = mock[MockDBFunctions]

  val configuration = Configuration("session-cache-expiry-time-seconds" → 1)

  val mongo: ReactiveMongoComponent = mock[ReactiveMongoComponent]

  lazy val mongoStore: SessionCacheImpl = {
    val connector = mock[MongoConnector]
    val db = stub[DefaultDB]
    (mongo.mongoConnector _).expects().returning(connector)
    (connector.db _).expects().returning(() ⇒ db)

    new SessionCacheImpl(configuration, mongo) {

      override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
        Future.successful(Seq.empty[Boolean])

      override def createOrUpdate(id: Id, key: String, toCache: JsValue): Future[DatabaseUpdate[Cache]] =
        mockDBFunctions.update(id, key, toCache)

      override def findById(id: Id, readPreference: ReadPreference)(implicit ec: ExecutionContext): Future[Option[Cache]] =
        mockDBFunctions.get(id, readPreference)

    }
  }

  def mockCreateOrUpdate(id: String, key: String, session: HTSSession)(result: Either[String, DatabaseUpdate[Cache]]): Unit =
    (mockDBFunctions.update(_: Id, _: String, _: JsValue))
      .expects(Id(id), key, Json.toJson(session))
      .returning(result.fold(
        e ⇒ Future.failed(new Throwable(e)),
        u ⇒ Future.successful(u)
      ))

  def mockGet(id: String)(result: Either[String, Option[Cache]]): Unit =
    (mockDBFunctions.get(_: Id, _: ReadPreference))
      .expects(Id(id), ReadPreference.primaryPreferred)
      .returning(result.fold(
        e ⇒ Future.failed(new Throwable(e)),
        r ⇒ Future.successful(r)
      ))

  def headerCarrierWithSessionID(id: Option[String]): HeaderCarrier =
    HeaderCarrier(sessionId = id.map(SessionId))

  "The SessionCacheImpl" when {

    val email = "email"
    val userInfo = NSIUserInfo(randomUserInfo())
    val sessionID = "session"

    val htsSession = HTSSession(Some(userInfo), Some(email))

    "starting" must {

      "throw an exception if there is no expiry time configured" in {
        an[ConfigException.Missing] shouldBe thrownBy(new SessionCacheImpl(Configuration("crap.key" → "crap value"), mongo))
      }

    }

    "storing" must {

        def databaseUpdate(successful: Boolean) =
          DatabaseUpdate(LastError(successful, None, None, None, None, 0, true),
            new Saved[Cache](Cache("")))

      "return an error" when {

        "there is no session ID in the header carrier" in {
          val hc = headerCarrierWithSessionID(None)
          await(mongoStore.store(htsSession)(hc, ec).value).isLeft shouldBe true
        }

        "if there is something wrong when updating mongo" in {
          val hc = headerCarrierWithSessionID(Some(sessionID))

          // test when the future fails
          mockCreateOrUpdate(sessionID, mongoStore.key, htsSession)(Left(""))
          await(mongoStore.store(htsSession)(hc, ec).value).isLeft shouldBe true

          // now test when there is an error
          mockCreateOrUpdate(sessionID, mongoStore.key, htsSession)(Right(databaseUpdate(false)))
          await(mongoStore.store(htsSession)(hc, ec).value).isLeft shouldBe true
        }

      }

      "return successfully otherwise" in {
        mockCreateOrUpdate(sessionID, mongoStore.key, htsSession)(Right(databaseUpdate(true)))

        val hc = headerCarrierWithSessionID(Some(sessionID))
        await(mongoStore.store(htsSession)(hc, ec).value) shouldBe Right(())
      }

    }

    "getting" must {

      "return an error" when {

        "there is no session ID in the header carrier" in {
          val hc = headerCarrierWithSessionID(None)

          await(mongoStore.get()(hc, ec).value).isLeft shouldBe true
        }

        "there is an error getting with mongo" in {
          val hc = headerCarrierWithSessionID(Some(sessionID))

          mockGet(sessionID)(Left(""))
          await(mongoStore.get()(hc, ec).value).isLeft shouldBe true
        }

        "the JSON returned by mongo does not contain the expected key" in {
          val hc = headerCarrierWithSessionID(Some(sessionID))

          mockGet(sessionID)(Right(Some(Cache("", Some(Json.parse(
            s"""
              |{
              |  "x" : ${Json.stringify(Json.toJson(htsSession))}
              |}
            """.stripMargin
          ))))))

          await(mongoStore.get()(hc, ec).value).isLeft shouldBe true

        }

        "the JSON return by mongo does does not contain a HTSSession" in {
          val hc = headerCarrierWithSessionID(Some(sessionID))

          mockGet(sessionID)(Right(Some(Cache("", Some(Json.parse(
            s"""
               |{
               |  "${mongoStore.key}" : {
               |    "eligibilityCheckResult" : "wrong data"
               |  }
               |}
            """.stripMargin
          ))))))

          val x = await(mongoStore.get()(hc, ec).value)
          x.isLeft shouldBe true
        }

      }

      "return None" when {

        "there is no data returned by mongo" in {
          val hc = headerCarrierWithSessionID(Some(sessionID))

          mockGet(sessionID)(Right(None))

          await(mongoStore.get()(hc, ec).value).fold[Unit](
            fail(_),
            _.isEmpty shouldBe true
          )
        }

      }

      "return Some HTSSession" when {

        "there is data returned by mongo" in {
          val hc = headerCarrierWithSessionID(Some(sessionID))

          mockGet(sessionID)(Right(Some(Cache("", Some(Json.parse(
            s"""
               |{
               |  "${mongoStore.key}" : ${Json.stringify(Json.toJson(htsSession))}
               |}
            """.stripMargin
          ))))))

          await(mongoStore.get()(hc, ec).value).fold[Unit](
            fail(_),
            _.contains(htsSession) shouldBe true
          )
        }

      }

    }
  }
}
