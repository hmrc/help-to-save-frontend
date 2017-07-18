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
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{nsiAuthHeaderKey, nsiBasicAuth, nsiUrl}
import uk.gov.hmrc.helptosavefrontend.config.WSHttpProxy
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionResult, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.play.http._

import scala.concurrent.{ExecutionContext, Future}

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
class NSIConnectorImpl extends NSIConnector {

  val httpProxy = new WSHttpProxy

  override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[SubmissionResult] = {

    Logger.info(s"Trying to create an account for ${userInfo.nino} using NSI endpoint $nsiUrl")
    Logger.info(s"CreateAccount json for ${userInfo.nino} is ${Json.toJson(userInfo)}")

    httpProxy.post(nsiUrl, userInfo, Map(nsiAuthHeaderKey → nsiBasicAuth))(
      NSIUserInfo.nsiUserInfoFormat, hc.copy(authorization = None))
      .map { response ⇒
        response.status match {
          case Status.CREATED ⇒
            Logger.info(s"Received 201 from NSI, successfully created account for ${userInfo.nino}")
            SubmissionSuccess()

          case Status.BAD_REQUEST ⇒
            Logger.error(s"Failed to create an account for ${userInfo.nino} due to bad request")
            handleBadRequestResponse(response)

          case other ⇒
            Logger.warn(s"Unexpected error during creating account for ${userInfo.nino}, status: $other")
            SubmissionFailure(None, s"Something unexpected happened; response body: ${response.body}", other.toString)
        } }
      }.recover {
    case e ⇒
      Logger.error("Encountered error while trying to create account", e)
      SubmissionFailure(None, s"Encountered error while trying to create account", e.getMessage)
  }

  private def handleBadRequestResponse(response: HttpResponse): SubmissionFailure = {
    response.parseJson[SubmissionFailure] match {
      case Right(submissionFailure) ⇒ submissionFailure
      case Left(error) ⇒ SubmissionFailure(None, "", error)
    }
  }
}
