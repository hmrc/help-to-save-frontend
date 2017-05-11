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


import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.helptosavefrontend.connectors.{IvConnector, SessionCacheConnector}
import uk.gov.hmrc.helptosavefrontend.models.iv.{IvSuccessResponse, JourneyId}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import play.api.mvc.{Result => PlayResult}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}


class HelpToSaveControllerSpec extends UnitSpec with WithFakeApplication with MockFactory {

  implicit val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]
  val mockAuthConnector = mock[AuthConnector]
  lazy val htsController = new HelpToSaveController {
    override def authConnector: AuthConnector = mockAuthConnector
  }

  import play.api.mvc.Results.Ok
  def doSomething(uri: String, nino: String): Future[PlayResult] = {
          Future.successful(Ok())
  }

  def doRequest(caseUri: String, nino: String): Future[PlayResult] = htsController.authorisedForHts(doSomething(caseUri, nino))(FakeRequest())


  "authorisedForHts" should {
    "return a 200 result ???????????" in {
      val caseUri = "/some-page"
      val nino = "WM123456C"

      val result = doRequest(caseUri, nino)
      status(result) shouldBe Status.OK

    }
  }

//  "handleFailure" should {
//    "redirect the user to the GGLogin page when there is currently no active session so they are not logged in" in {
//      //do a request that returns a NoActiveSession
//
//    }
//  }


}

