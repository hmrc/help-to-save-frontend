/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import cats.data.EitherT
import com.google.inject.ImplementedBy
import play.api.http.Status
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosavefrontend.config.{FrontendAppConfig, WSHttp}
import uk.gov.hmrc.helptosavefrontend.connectors.NSIProxyConnector.{SubmissionFailure, SubmissionResult, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIUserInfo.nsiUserInfoFormat
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.{Logging, NINOLogMessageTransformer, Result, maskNino}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[NSIProxyConnectorImpl])
trait NSIProxyConnector {
  def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[SubmissionResult]

  def updateEmail(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Unit]

}

object NSIProxyConnector {

  sealed trait SubmissionResult

  case class SubmissionSuccess() extends SubmissionResult

  case class SubmissionFailure(errorMessageId: Option[String], errorMessage: String, errorDetail: String) extends SubmissionResult

  implicit val submissionFailureFormat: Format[SubmissionFailure] = Json.format[SubmissionFailure]

}

@Singleton
class NSIProxyConnectorImpl @Inject() (http: WSHttp)(implicit transformer: NINOLogMessageTransformer, frontendAppConfig: FrontendAppConfig)
  extends NSIProxyConnector with Logging {

  override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SubmissionResult] = {
    http.post(frontendAppConfig.nsiCreateAccountUrl, userInfo).map[SubmissionResult] { response ⇒

      response.status match {
        case Status.CREATED | Status.CONFLICT ⇒
          SubmissionSuccess()

        case _ ⇒
          handleError(response)
      }
    }.recover {
      case e ⇒
        SubmissionFailure(None, "Encountered error while trying to create account", e.getMessage)
    }
  }

  override def updateEmail(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] = EitherT[Future, String, Unit] {
    http.put(frontendAppConfig.nsiUpdateEmailUrl, userInfo)
      .map[Either[String, Unit]] { response ⇒

        response.status match {
          case Status.OK ⇒
            Right(())

          case other ⇒
            Left(s"Received unexpected status $other from NS&I proxy while trying to update email. Body was ${maskNino(response.body)}")

        }
      }.recover {
        case e ⇒
          Left(s"Encountered error while trying to update email: ${e.getMessage}")
      }
  }

  private def handleError(response: HttpResponse): SubmissionFailure = {
    logger.warn(s"response body from NS&I proxy=${maskNino(response.body)}")
    response.parseJSON[SubmissionFailure](Some("error")) match {
      case Right(submissionFailure) ⇒ submissionFailure
      case Left(error)              ⇒ SubmissionFailure(None, "", error)
    }
  }
}
