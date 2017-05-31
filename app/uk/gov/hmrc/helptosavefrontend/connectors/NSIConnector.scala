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
import play.api.libs.json.{Format, JsError, JsSuccess, Json}
import uk.gov.hmrc.helptosavefrontend.config.WSHttpProxy
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionResult, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.util.JsErrorOps._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@ImplementedBy(classOf[NSIConnectorImpl])
trait NSIConnector {
  def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[SubmissionResult]
}

object NSIConnector {

  sealed trait SubmissionResult

  case class SubmissionSuccess() extends SubmissionResult

  case class SubmissionFailure(errorMessageId: Option[String], errorMessage: String, errorDetail: String) extends SubmissionResult

  implicit val submissionFailureFormat: Format[SubmissionFailure] = Json.format[SubmissionFailure]

}

@Singleton
class NSIConnectorImpl extends NSIConnector with ServicesConfig {

  val nsiUrl: String = baseUrl("nsi")
  val nsiUrlEnd: String = getString("microservice.services.nsi.url")
  val url = s"$nsiUrl/$nsiUrlEnd"

  val httpProxy = new WSHttpProxy

  override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[SubmissionResult] = {
    Logger.info(s"Trying to create an account for ${userInfo.NINO}")
    httpProxy.post(url, userInfo)
      .map { response ⇒
        response.status match {
          case Status.CREATED ⇒
            Logger.info(s"Successfully created a NSI account for ${userInfo.NINO}")
            SubmissionSuccess()

          case Status.BAD_REQUEST ⇒
            Logger.error(s"Failed to create an account for ${userInfo.NINO} due to bad request")
            handleBadRequestResponse(response)

          case other ⇒
            Logger.warn(s"Unexpected error during creating account for ${userInfo.NINO}, status:$other ")
            SubmissionFailure(None, s"Something unexpected happened; response body: ${response.body}", other.toString)
        }
      }
  }


  private def handleBadRequestResponse(response: HttpResponse): SubmissionFailure = {
    Try(response.json) match {
      case Success(jsValue) ⇒
        Json.fromJson[SubmissionFailure](jsValue) match {
          case JsSuccess(submissionFailure, _) ⇒
            submissionFailure

          case e: JsError ⇒
            SubmissionFailure(None, s"Could not create NSI account errors; response body: ${response.body}", e.prettyPrint())
        }

      case Failure(error) ⇒
        SubmissionFailure(None, s"Could not read submission failure JSON response: ${response.body}", error.getMessage)

    }

  }
}
