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

import javax.inject.Singleton

import com.google.inject.ImplementedBy
import play.api.Logger
import play.api.http.Status
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.connectors.CreateAccountConnector.{SubmissionFailure, SubmissionResult, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.models.UserInfo
import uk.gov.hmrc.helptosavefrontend.util.JsErrorOps._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.ws.WSPost

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[CreateAccountConnectorImpl])
trait CreateAccountConnector {
  def createAccount(userInfo: UserInfo)(implicit hc: HeaderCarrier,ex :ExecutionContext): Future[SubmissionResult]
}

object CreateAccountConnector {

  sealed trait SubmissionResult
  case object SubmissionSuccess extends SubmissionResult
  case class SubmissionFailure(errorMessageId:Option[String], errorMessage:String, errorDetail:String)  extends SubmissionResult

  implicit val submissionFailureFormat: Format[SubmissionFailure] = Json.format[SubmissionFailure]

}

@Singleton
class CreateAccountConnectorImpl extends CreateAccountConnector with ServicesConfig {

  val helpToSaveUrl: String = baseUrl("help-to-save-eligibility")
  val createAccountUrlEnd: String = getString("microservice.services.help-to-save-eligibility.url")
  val http: WSPost = WSHttp
  val url = s"$helpToSaveUrl/$createAccountUrlEnd"
  override def createAccount(userInfo: UserInfo)(implicit hc: HeaderCarrier,ex : ExecutionContext): Future[SubmissionResult]= {
    Logger.debug("Creating NSI Account " + url)
      http.POST[UserInfo, HttpResponse](url, userInfo).map { response =>
        response.status match {
          case Status.CREATED ⇒
            SubmissionSuccess
          case Status.BAD_REQUEST  ⇒
            Logger.error("Submission Failure to NSI")
            Json.fromJson[SubmissionFailure](response.json) match {
            case JsSuccess(failure, _) ⇒
              failure
            case e: JsError ⇒
              SubmissionFailure(None, s"Could not NSI errors",e.prettyPrint())
          }
          case other ⇒
            Logger.warn("Failed post to NSI " + other.toString)
            SubmissionFailure(None, s"Bad Status", other.toString)
        }
      }
  }
}
