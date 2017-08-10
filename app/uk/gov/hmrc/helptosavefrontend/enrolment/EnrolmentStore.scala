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

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject}
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.helptosavefrontend.enrolment.EnrolmentStore.{Enrolled, NotEnrolled, Status}
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentData
import uk.gov.hmrc.helptosavefrontend.util.NINO
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MongoEnrolmentStore])
trait EnrolmentStore {

  import EnrolmentStore._

  def get(nino: NINO): EitherT[Future, String, Status]

  def update(data: EnrolmentData): EitherT[Future, String, Unit]

}

object EnrolmentStore {

  sealed trait Status {
    def fold[T](ifNotEnrolled: ⇒ T, ifEnrolled: Boolean ⇒ T): T = this match {
      case e: Enrolled ⇒ ifEnrolled(e.itmpHtSFlag)
      case NotEnrolled ⇒ ifNotEnrolled
    }
  }

  case class Enrolled(itmpHtSFlag: Boolean) extends Status

  case object NotEnrolled extends Status

}

class MongoEnrolmentStore @Inject()(mongo: ReactiveMongoComponent)(implicit ec: ExecutionContext)
  extends ReactiveRepository[EnrolmentData, BSONObjectID](
    collectionName = "enrolments",
    mongo = mongo.mongoConnector.db,
    EnrolmentData.ninoFormat,
    ReactiveMongoFormats.objectIdFormats)
    with EnrolmentStore {

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("nino" → IndexType.Ascending),
      name = Some("ninoIndex")
    )
  )

  private[enrolment] def doUpdate(data: EnrolmentData)(implicit ec: ExecutionContext): Future[Option[EnrolmentData]] = {

    val fields = data.email.fold(List(BSONDocument("itmpHtSFlag" -> data.itmpHtSFlag)))(
      email ⇒ List(BSONDocument("itmpHtSFlag" -> data.itmpHtSFlag), BSONDocument("email" -> email))
    )

    collection.findAndUpdate(
      BSONDocument("nino" -> data.nino),
      BSONDocument("$set" -> fields),
      fetchNewObject = true,
      upsert = true
    ).map(_.result[EnrolmentData])
  }

  override def get(nino: String): EitherT[Future, String, EnrolmentStore.Status] = EitherT(
    find("nino" → JsString(nino)).map { res ⇒
      Right(res.headOption.fold[Status](NotEnrolled)(data ⇒ Enrolled(data.itmpHtSFlag)))
    }.recover {
      case e ⇒
        logger.error(s"Could not read from enrolment store", e)
        Left(s"Could not read from enrolment store: ${e.getMessage}")
    })

  override def update(data: EnrolmentData): EitherT[Future, String, Unit] = {
    logger.info(s"Putting nino ${data.nino} into enrolment store")
    EitherT(
      doUpdate(data).map[Either[String, Unit]] { result ⇒
        result.fold[Either[String, Unit]](
          Left("Could not update enrolment store")
        ) { _ ⇒
          logger.info("Successfully updated enrolment store")
          Right(())
        }
      }.recover { case e ⇒
        logger.error(s"Could not write to enrolment store", e)
        Left(s"Failed to write to enrolments store: ${e.getMessage}")
      }
    )
  }
}
