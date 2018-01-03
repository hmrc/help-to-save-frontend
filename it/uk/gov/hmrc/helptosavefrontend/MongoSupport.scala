package uk.gov.hmrc.helptosavefrontend

import org.scalatest.Suite
import play.api.libs.json.{Json, Reads}
import play.api.libs.json.Json.JsValueWrapper
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor
import reactivemongo.api.commands.WriteResult
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.ImplicitBSONHandlers._

import scala.concurrent.{ExecutionContext, Future}

trait MongoSupport { this: IntegrationTest with Suite ⇒

  lazy val mongoDB = application.injector.instanceOf[ReactiveMongoComponent].mongoConnector.db()

  def collection(id: String): JSONCollection = mongoDB.collection[JSONCollection](id)

}

object MongoSupport {

  implicit class JSONCollectionOps(val collection: JSONCollection) extends AnyVal {
    def findAll[A](query: (String, JsValueWrapper)*)(implicit reads: Reads[A], ec: ExecutionContext): Future[List[A]] =
      collection.find(Json.obj(query: _*)).cursor[A]().collect[List](-1, Cursor.FailOnError[List[A]]())

    def removeAll(query: (String, JsValueWrapper)*)(implicit ec: ExecutionContext): Future[WriteResult] =
      collection.remove(Json.obj(query: _*))
  }

}
