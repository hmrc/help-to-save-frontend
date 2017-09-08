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

import javax.inject.{Inject, Singleton}

import com.codahale.metrics.Timer
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{nsiAuthHeaderKey, nsiBasicAuth, nsiUrl}
import uk.gov.hmrc.helptosavefrontend.config.WSHttpProxy
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionResult, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.models.{ApplicationSubmittedEvent, NSIUserInfo}
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.{HTSAuditor, Logging}
import uk.gov.hmrc.helptosavefrontend.util.Time.nanosToPrettyString
import uk.gov.hmrc.play.config.AppName
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
class NSIConnectorImpl @Inject() (conf: Configuration, auditor: HTSAuditor, metrics: Metrics) extends NSIConnector with Logging with AppName {

  val timer: Timer = metrics.defaultRegistry.timer("nsi-account-creation-time-ns")

  val httpProxy: WSHttpProxy = new WSHttpProxy

  override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[SubmissionResult] = {
    import uk.gov.hmrc.helptosavefrontend.util.Toggles._

    logger.info(s"Trying to create an account for ${userInfo.nino} using NSI endpoint $nsiUrl")

    FEATURE("log-account-creation-json", conf, logger).thenOrElse(
      logger.info(s"CreateAccount json for ${userInfo.nino} is ${Json.toJson(userInfo)}"),
      ()
    )

    val timeContext: Timer.Context = timer.time()

    httpProxy.post(nsiUrl, userInfo, Map(nsiAuthHeaderKey → nsiBasicAuth))
      .map[SubmissionResult] { response ⇒
        val time = timeContext.stop()

        response.status match {
          case Status.CREATED ⇒
            auditor.sendEvent(new ApplicationSubmittedEvent(appName, userInfo))
            logger.info(s"Received 201 from NSI, successfully created account for ${userInfo.nino} ${timeString(time)}")
            SubmissionSuccess()

          case Status.BAD_REQUEST ⇒
            logger.warn(s"Failed to create an account for ${userInfo.nino} due to bad request ${timeString(time)}")
            handleBadRequestResponse(response)

          case Status.INTERNAL_SERVER_ERROR ⇒
            logger.warn(s"Received 500 from NSI, failed to create account for ${userInfo.nino} as there was an " +
              s"internal server error ${timeString(time)}")
            handleBadRequestResponse(response)

          case Status.SERVICE_UNAVAILABLE ⇒
            logger.warn(s"Received 503 from NSI, failed to create account for ${userInfo.nino} as NSI " +
              s"service is unavailable ${timeString(time)}")
            handleBadRequestResponse(response)

          case other ⇒
            logger.warn(s"Unexpected error during creating account for ${userInfo.nino}, status : $other ${timeString(time)}")
            SubmissionFailure(None, s"Something unexpected happened; response body: ${response.body}", other.toString)
        }
      }.recover {
        case e ⇒
          val time = timeContext.stop()
          logger.warn(s"Encountered error while trying to create account ${timeString(time)}", e)
          SubmissionFailure(None, "Encountered error while trying to create account", e.getMessage)
      }
  }

  private def timeString(nanos: Long): String = s"(time: ${nanosToPrettyString(nanos)})"

  private def handleBadRequestResponse(response: HttpResponse): SubmissionFailure = {
    response.parseJson[SubmissionFailure] match {
      case Right(submissionFailure) ⇒ submissionFailure
      case Left(error)              ⇒ SubmissionFailure(None, "", error)
    }
  }
}
