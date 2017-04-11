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

package uk.gov.hmrc.helptosavefrontend.controllers

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cats.syntax.show._
import org.scalatest.BeforeAndAfterAll
import play.api.http.Status
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavefrontend.connectors.FakeEligibilityConnector
import uk.gov.hmrc.helptosavefrontend.connectors.FakeEligibilityConnector.CheckEligibility
import uk.gov.hmrc.helptosavefrontend.models.UserDetails.localDateShow
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class DeclarationSpec extends TestKit(ActorSystem("HelpToSaveSpec")) with ImplicitSender
   with UnitSpec with BeforeAndAfterAll with WithFakeApplication{

  override def afterAll {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  val fakeRequest = FakeRequest("GET", "/")

  val helpToSave = new HelpToSave(new FakeEligibilityConnector(self))

  def doRequest(): Future[Result] = helpToSave.declaration(fakeRequest)

  "GET /" should {

    "call getEligibility from the given EligibilityStubConnector" in {
      doRequest()
      expectMsgType[CheckEligibility].promise
    }

    "return 200 if the eligibility check is successful" in {
      val result: Future[Result] = doRequest()

      val check = expectMsgType[CheckEligibility].promise
      check.success(randomUserDetails())

      status(result) shouldBe Status.OK
    }

    "return HTML if the eligibility check is successful" in {
      val result = doRequest()

      val check = expectMsgType[CheckEligibility].promise
      check.success(randomUserDetails())

      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }

    "display the user details if the eligibility check is successful" in {
      val user = randomUserDetails()

      val result = doRequest()
      val check = expectMsgType[CheckEligibility].promise
      check.success(user)
      val html = contentAsString(result)

      html should include(user.name)
      html should include(user.dateOfBirth.show)
      html should include(user.email)
      html should include(user.NINO)
      html should include(user.phoneNumber)
      html should include(user.address.mkString(","))
      html should include(user.contactPreference.show)
    }
  }
}

