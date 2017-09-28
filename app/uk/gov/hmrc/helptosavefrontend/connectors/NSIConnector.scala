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

import cats.data.EitherT
import com.codahale.metrics.Timer
import com.google.inject.ImplementedBy
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosavefrontend.audit.HTSAuditor
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{nsiAuthHeaderKey, nsiBasicAuth, nsiCreateAccountUrl, nsiUpdateEmailUrl}
import uk.gov.hmrc.helptosavefrontend.config.WSHttpProxy
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionResult, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosavefrontend.models.{ApplicationSubmittedEvent, NSIUserInfo}
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.{Logging, NINO, Result}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.config.AppName

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[NSIConnectorImpl])
trait NSIConnector {
  def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[SubmissionResult]

  def updateEmail(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Unit]

}

object NSIConnector {

  sealed trait SubmissionResult

  case class SubmissionSuccess() extends SubmissionResult

  case class SubmissionFailure(errorMessageId: Option[String], errorMessage: String, errorDetail: String) extends SubmissionResult

  implicit val submissionFailureFormat: Format[SubmissionFailure] = Json.format[SubmissionFailure]

}

@Singleton
class NSIConnectorImpl @Inject() (conf: Configuration, auditor: HTSAuditor, metrics: Metrics) extends NSIConnector with Logging with AppName {

  val httpProxy: WSHttpProxy = new WSHttpProxy

  override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[SubmissionResult] = {
    import uk.gov.hmrc.helptosavefrontend.util.Toggles._

    logger.info(s"Trying to create an account for ${userInfo.nino} using NSI endpoint $nsiCreateAccountUrl")

    FEATURE("log-account-creation-json", conf, logger).thenOrElse(
      logger.info(s"CreateAccount json for ${userInfo.nino} is ${Json.toJson(userInfo)}"),
      ()
    )

    val timeContext: Timer.Context = metrics.nsiAccountCreationTimer.time()

    httpProxy.post(nsiCreateAccountUrl, userInfo, Map(nsiAuthHeaderKey → nsiBasicAuth))
      .map[SubmissionResult] { response ⇒
        val time = timeContext.stop()

        response.status match {
          case Status.CREATED ⇒
            auditor.sendEvent(ApplicationSubmittedEvent(appName, userInfo))
            logger.info(s"Received 201 from NSI, successfully created account for ${userInfo.nino} ${timeString(time)}")
            SubmissionSuccess()

          case other ⇒
            handleErrorStatus(other, response, userInfo.nino, time)
        }
      }.recover {
        case e ⇒
          val time = timeContext.stop()
          metrics.nsiAccountCreationErrorCounter.inc()

          logger.warn(s"Encountered error while trying to create account ${timeString(time)}", e)
          SubmissionFailure(None, "Encountered error while trying to create account", e.getMessage)
      }
  }

  override def updateEmail(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Unit] = EitherT[Future, String, Unit]{
    val timeContext: Timer.Context = metrics.nsiUpdateEmailTimer.time()

    httpProxy.put(nsiUpdateEmailUrl, userInfo, Map(nsiAuthHeaderKey → nsiBasicAuth))
      .map[Either[String, Unit]] { response ⇒
        val time = timeContext.stop()

        response.status match {
          case Status.OK ⇒
            logger.info(s"Received 200 from NSI, successfully updated email for ${userInfo.nino} ${timeString(time)}")
            Right(())

          case other ⇒
            metrics.nsiUpdateEmailErrorCounter.inc()
            Left(s"Received unexpected status $other from NS&I while trying to update email ${timeString(time)}. " +
              s"Body was ${response.body}")

        }
      }.recover {
        case e ⇒
          val time = timeContext.stop()
          metrics.nsiUpdateEmailErrorCounter.inc()

          Left(s"Encountered error while trying to create account: ${e.getMessage} ${timeString(time)}")
      }
  }

  private def handleErrorStatus(status: Int, response: HttpResponse, nino: NINO, time: Long) = {
    metrics.nsiAccountCreationErrorCounter.inc()

    status match {
      case Status.BAD_REQUEST ⇒
        logger.warn(s"Failed to create an account for $nino due to bad request ${timeString(time)}")
        handleBadRequestResponse(response)

      case Status.INTERNAL_SERVER_ERROR ⇒
        logger.warn(s"Received 500 from NSI, failed to create account for $nino as there was an " +
          s"internal server error ${timeString(time)}")
        handleBadRequestResponse(response)

      case Status.SERVICE_UNAVAILABLE ⇒
        logger.warn(s"Received 503 from NSI, failed to create account for $nino as NSI " +
          s"service is unavailable ${timeString(time)}")
        handleBadRequestResponse(response)

      case other ⇒
        logger.warn(s"Unexpected error during creating account for $nino, status : $other ${timeString(time)}")
        SubmissionFailure(None, s"Something unexpected happened; response body: ${response.body}", other.toString)
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
