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

package uk.gov.hmrc.helptosavefrontend.connectors

import java.net.URLEncoder

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.Json
import uk.gov.hmrc.helptosavefrontend.config.{WSHttp, WSHttpExtension}
import uk.gov.hmrc.helptosavefrontend.models.{EligibilityResult, UserInfo}
import uk.gov.hmrc.helptosavefrontend.util.{NINO, Result}
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def getEligibilityStatus(nino: NINO, userDetailsURI: String)(implicit hc: HeaderCarrier): Result[EligibilityResult]

}

@Singleton
class HelpToSaveConnectorImpl @Inject()(implicit ec: ExecutionContext) extends HelpToSaveConnector with ServicesConfig {

  val helpToSaveUrl: String = baseUrl("help-to-save")

  val createAccountURL: String = s"$helpToSaveUrl/help-to-save/create-an-account"

  def eligibilityURL(nino: NINO, userDetailsURI: String): String =
    s"$helpToSaveUrl/help-to-save/eligibility-check?nino=$nino&userDetailsURI=${URLEncoder.encode(userDetailsURI, "UTF-8")}"

  /**
    * @param response The HTTPResponse which came back with a bad status
    * @param service  The call we tried to make
    * @return a string describing an error response from a HTTP call
    */
  def badResponseMessage(response: HttpResponse, service: String): String =
    s"$service call returned with status ${response.status}. Response body was ${response.body}"

  val http: WSHttpExtension = WSHttp

  override def getEligibilityStatus(nino: NINO, userDetailsURI: String)(implicit hc: HeaderCarrier): Result[EligibilityResult] =
    EitherT.right[Future, String, HttpResponse](http.get(eligibilityURL(nino, userDetailsURI)))
      .subflatMap { response ⇒
        if (response.status == 200) {
          response.parseJson[EligibilityResult]
        } else {
          Left(badResponseMessage(response, "Eligibility check"))
        }
      }

}