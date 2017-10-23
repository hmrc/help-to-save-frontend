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

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest._
import org.scalatestplus.play.{BaseOneServerPerSuite, FakeApplicationFactory, OneServerPerSuite}
import play.api.http.HeaderNames
import play.api.libs.ws.WS
import play.api.test.FakeApplication
import uk.gov.hmrc.crypto.ApplicationCrypto._
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth._
import uk.gov.hmrc.helptosavefrontend.testutil.{TestSupportWithWiremock, WiremockHelper}
import uk.gov.hmrc.helptosavefrontend.testutil.ContactStubs.stubGetContactForm

class HelpAndContactControllerSpec2 extends TestSupportWithWiremock {

  override implicit lazy val app: FakeApplication = FakeApplication(additionalConfiguration = Map(
    "microservice.services.email-verification.host" -> WiremockHelper.wiremockHost,
    "microservice.services.email-verification.port" -> WiremockHelper.wiremockPort
  ))

  feature("report a problem") {

    val continueUrl = "/continue-url"

      def jsonToken(token: String) =
        s"""
         |{
         | "token": "$token",
         | "continueUrl": "$continueUrl"
         |}
        """.stripMargin
      def encryptAndEncode(value: String) = new String(QueryParameterCrypto.encrypt(PlainText(value)).toBase64)

    scenario("a user clicks on 'Get help with this page' button"){
      Given("the user is logged in")
      val token = UUID.randomUUID().toString
      val encryptedJsonToken = encryptAndEncode(jsonToken(token))

      stubGetContactForm(token, 200)

      When("they click on 'Get help with this page' link")
      val response = client("/help").withQueryString("token" -> encryptedJsonToken).get().futureValue

      Then("the response should be 200")
      response.status shouldBe 200

      And("the user is directed to the report a problem page")
      response.header(HeaderNames.LOCATION) should contain("/help-to-save/help")

    }
  }

  override def client(path: String) = WS.url(s"http://localhost:$port/help-to-save$path").withFollowRedirects(false)

}
