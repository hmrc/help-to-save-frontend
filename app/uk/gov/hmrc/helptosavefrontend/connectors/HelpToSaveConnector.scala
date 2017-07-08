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

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig._
import uk.gov.hmrc.helptosavefrontend.config.{WSHttp, WSHttpExtension}
import uk.gov.hmrc.helptosavefrontend.models.EligibilityCheckResult
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.{NINO, Result}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def getEligibility(nino: NINO, oauthCode: String)(implicit hc: HeaderCarrier): Result[EligibilityCheckResult]
}

@Singleton
class HelpToSaveConnectorImpl @Inject()(implicit ec: ExecutionContext) extends HelpToSaveConnector {


  def eligibilityURL(nino: NINO, oauthCode: String) = s"$eligibilityCheckUrl?nino=$nino&oauthAuthorisationCode=$oauthCode"

  /**
    * @param response The HTTPResponse which came back with a bad status
    * @param service  The call we tried to make
    * @return a string describing an error response from a HTTP call
    */
  def badResponseMessage(response: HttpResponse, service: String): String =
    s"$service call returned with status ${response.status}. Response body was ${response.body}"

  val http: WSHttpExtension = WSHttp

  override def getEligibility(nino: NINO,
                              oauthCode: String)(implicit hc: HeaderCarrier): Result[EligibilityCheckResult] =
    EitherT.right[Future, String, HttpResponse](http.get(eligibilityURL(nino, oauthCode)))
      .subflatMap { response ⇒
        if (response.status == 200) {
          response.parseJson[EligibilityCheckResult]
        } else {
          Left(badResponseMessage(response, "Eligibility check"))
        }
      }
}