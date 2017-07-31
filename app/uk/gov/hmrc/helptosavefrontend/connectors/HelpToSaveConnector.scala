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
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig._
import uk.gov.hmrc.helptosavefrontend.config.{WSHttp, WSHttpExtension}
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl.EligibilityCheckResponse
import uk.gov.hmrc.helptosavefrontend.models.EligibilityCheckError.{BackendError, MissingUserInfos}
import uk.gov.hmrc.helptosavefrontend.models.{EligibilityCheckError, EligibilityCheckResult, MissingUserInfo, UserInfo}
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.{NINO, UserDetailsURI}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def getEligibility(nino: NINO, oauthCode: String)(implicit hc: HeaderCarrier): EitherT[Future,EligibilityCheckError,EligibilityCheckResult]
}

@Singleton
class HelpToSaveConnectorImpl @Inject()(implicit ec: ExecutionContext) extends HelpToSaveConnector {


  def eligibilityURL(nino: NINO, userDetailsURI: String) =
    s"$eligibilityCheckUrl?nino=$nino&userDetailsURI=${encoded(userDetailsURI)}"

  /**
    * @param response The HTTPResponse which came back with a bad status
    * @param service  The call we tried to make
    * @return a string describing an error response from a HTTP call
    */
  def badResponseMessage(response: HttpResponse, service: String): String =
    s"$service call returned with status ${response.status}. Response body was ${response.body}"

  val http: WSHttpExtension = WSHttp

  override def getEligibility(nino: NINO,
                              userDetailsURI: UserDetailsURI)(implicit hc: HeaderCarrier): EitherT[Future,EligibilityCheckError,EligibilityCheckResult] =
    EitherT.right[Future, String, HttpResponse](http.get(eligibilityURL(nino, userDetailsURI)))
      .leftMap(s ⇒ BackendError(s, nino))
      .subflatMap{ response ⇒
        if (response.status == 200) {
          response.parseJson[EligibilityCheckResponse].fold(
            e ⇒ Left(BackendError(e, nino)),
            _.result.fold(
              m ⇒ Left(MissingUserInfos(m.missingInfo, nino)),
              u ⇒ Right(EligibilityCheckResult(u))
            )
          )
        } else {
          Left(BackendError(badResponseMessage(response, "Eligibility check"), nino))
        }
      }
}


object HelpToSaveConnectorImpl {

  private[connectors] case class MissingUserInfoSet(missingInfo: Set[MissingUserInfo])

  private[connectors] object MissingUserInfoSet {
    implicit val missingUserInfoSetFormat: Format[MissingUserInfoSet] =
      Json.format[MissingUserInfoSet]
  }

  private[connectors] case class EligibilityCheckResponse(result: Either[MissingUserInfoSet, Option[UserInfo]])

  private[connectors] object EligibilityCheckResponse {

    implicit val eligibilityResultFormat: Format[EligibilityCheckResponse] = new Format[EligibilityCheckResponse] {
      override def reads(json: JsValue): JsResult[EligibilityCheckResponse] = {
        (json \ "result").toOption match {
          case None ⇒
            JsError("Could not find 'result' path in JSON")

          case Some(jsValue) ⇒
            jsValue.validate[MissingUserInfoSet].fold(e1 ⇒
              jsValue.validateOpt[UserInfo].fold(e2 ⇒
                JsError(e1 ++ e2),
                maybeUserInfo ⇒ JsSuccess(EligibilityCheckResponse(Right(maybeUserInfo)))
              ),
              missing ⇒ JsSuccess(EligibilityCheckResponse(Left(missing)))
            )
        }
      }

      override def writes(o: EligibilityCheckResponse): JsValue = Json.obj(
        o.result.fold(
          missingInfos ⇒ "result" -> Json.toJson(missingInfos),
          maybeUserInfo ⇒ "result" -> Json.toJson(maybeUserInfo)
        ))
    }
  }

}