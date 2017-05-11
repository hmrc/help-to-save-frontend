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

import cats.data.EitherT
import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import com.google.inject.ImplementedBy
import play.api.http.Status
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.WSHttp
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionResult, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.helptosavefrontend.util.JsErrorOps._
import uk.gov.hmrc.play.http.ws.WSPost

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[NSIConnectorImpl])
trait NSIConnector {
  def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier,ex :ExecutionContext): Future[SubmissionResult]
}

object NSIConnector {

  sealed trait SubmissionResult
  case object SubmissionSuccess extends SubmissionResult
  case class SubmissionFailure(errorMessageId:Option[String], errorMessage:String, errorDetail:String)  extends SubmissionResult

  implicit val submissionFailureFormat: Format[SubmissionFailure] = Json.format[SubmissionFailure]

}

@Singleton
class NSIConnectorImpl extends NSIConnector with ServicesConfig {

  val nsiUrl: String = baseUrl("nsi")
  val nsiUrlEnd: String = getString("microservice.services.nsi.url")

  val encodedAuthorisation: String = {
    val userName: String = getString("microservice.services.nsi.username")
    val password: String = getString("microservice.services.nsi.password")
    BaseEncoding.base64().encode((userName + ":" + password).getBytes(Charsets.UTF_8))
  }

  val http: WSPost = WSHttp

  override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier,ex : ExecutionContext): Future[SubmissionResult]= {
      http.POST[NSIUserInfo, HttpResponse](s"$nsiUrl/$nsiUrlEnd", userInfo,
        headers = Seq(("Authorization", encodedAuthorisation))).map { response =>
        response.status match {
          case Status.CREATED ⇒
            SubmissionSuccess

          case Status.BAD_REQUEST  ⇒
            Json.fromJson[SubmissionFailure](response.json) match {
            case JsSuccess(failure, _) ⇒ failure
            case e: JsError ⇒ SubmissionFailure(None, s"Could not NSI errors",e.prettyPrint())
          }
          case other ⇒ SubmissionFailure(None, s"Bad Status", other.toString)
        }
      }
  }
}
