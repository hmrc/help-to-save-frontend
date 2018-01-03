package uk.gov.hmrc.helptosavefrontend

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import play.api.Play
import play.api.test.FakeApplication

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

trait IntegrationTest extends WordSpec with Matchers with BeforeAndAfterAll {

  lazy val application = FakeApplication()

  implicit lazy val ec: ExecutionContext = application.injector.instanceOf[ExecutionContext]

  def await[A](f: Future[A]): A = Await.result(f, 10.seconds)

  override def beforeAll(): Unit = {
    super.beforeAll()
    Play.start(application)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    Play.stop(application)
  }
}
