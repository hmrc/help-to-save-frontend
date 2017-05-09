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

import play.api.libs.json.{JsValue, Json, Writes}
import play.api.test.Helpers.OK
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.http.hooks.HttpHook
import uk.gov.hmrc.play.http.ws.WSHttp

import scala.concurrent.Future

/**
  * Created by suresh on 11/05/2017.
  */
trait MockAuthSupport {

  val nino = "WM123456C"

  val userDetailsUri = "/dummy/user/details/uri"

  val withBody: Option[JsValue] = Some(Json.parse(
    s"""{
       "userDetailsUri": "/dummy/user/details/uri",
       "allEnrolments": [
          {
           "key": "HMRC-NI",
           "identifiers": [{"key":"NINO","value":"WM123456C"}],
           "state": "Activated",
           "confidenceLevel": 200
          }
       ]}
    """.stripMargin
  ))

  val mockPlayAuth = new PlayAuthConnector {
    override lazy val http = new WSHttp {
      override val hooks: Seq[HttpHook] = NoneRequired

      override def doPost[A](url: String, body: A, headers: Seq[(String, String)])(implicit rds: Writes[A], hc: HeaderCarrier): Future[HttpResponse] = {
        val httpResponse = HttpResponse(OK, responseJson = withBody, responseHeaders = Map.empty)
        Future.successful(httpResponse)
      }
    }

    override val serviceUrl: String = "/localhost/auth/authorise"
  }

}
