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

package uk.gov.hmrc.helptosavefrontend.testutil

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest._
import org.scalatestplus.play.{BaseOneServerPerSuite, FakeApplicationFactory, OneServerPerSuite}
import play.api.libs.ws.WS
import uk.gov.hmrc.helptosavefrontend.controllers.AuthSupport

object WiremockHelper {
  val wiremockPort = 11111
  val wiremockHost = "localhost"
  val url = s"http://$wiremockHost:$wiremockPort"
}

trait WiremockHelper {

  import uk.gov.hmrc.helptosavefrontend.testutil.WiremockHelper._

  val wmConfig = wireMockConfig().port(wiremockPort)
  val wireMockServer = new WireMockServer(wmConfig)

  def startWiremock() = {
    wireMockServer.start()
    WireMock.configureFor(wiremockHost, wiremockPort)
  }

  def stopWiremock() = wireMockServer.stop()

  def resetWiremock() = WireMock.reset()

}

trait TestSupportWithWiremock extends FeatureSpec with GivenWhenThen with OneServerPerSuite with ScalaFutures with IntegrationPatience with Matchers with WiremockHelper with BeforeAndAfterEach with BeforeAndAfterAll {

  override def beforeAll() {
    startWiremock()
  }

  override def afterAll() {
    stopWiremock()
  }

  def client(path: String) = WS.url(s"http://localhost:$port/help-to-save$path").withFollowRedirects(false)

  def contactClient(path: String) = WS.url(s"http://localhost:$port/contact$path").withFollowRedirects(false)
}
