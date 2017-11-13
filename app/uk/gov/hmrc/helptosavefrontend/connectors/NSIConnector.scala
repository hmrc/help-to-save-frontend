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
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{nsiAuthHeaderKey, nsiBasicAuth, nsiCreateAccountUrl}
import uk.gov.hmrc.helptosavefrontend.config.WSHttpProxy
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionResult, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIUserInfo.nsiUserInfoFormat
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util.{Logging, NINO, Result}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.config.AppName

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[NSIConnectorImpl])
trait NSIConnector {
  def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[SubmissionResult]

  def updateEmail(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Unit]

  def healthCheck(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Unit]

}

object NSIConnector {

  sealed trait SubmissionResult

  case class SubmissionSuccess() extends SubmissionResult

  case class SubmissionFailure(errorMessageId: Option[String], errorMessage: String, errorDetail: String) extends SubmissionResult

  implicit val submissionFailureFormat: Format[SubmissionFailure] = Json.format[SubmissionFailure]

}

@Singleton
class NSIConnectorImpl @Inject() (conf: Configuration, metrics: Metrics) extends NSIConnector with Logging with AppName {

  val httpProxy: WSHttpProxy = new WSHttpProxy

  override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SubmissionResult] = {
    import uk.gov.hmrc.helptosavefrontend.util.Toggles._

    val nino = userInfo.nino

    logger.info(s"Trying to create an account using NSI endpoint $nsiCreateAccountUrl", nino)

    FEATURE("log-account-creation-json", conf, logger).thenOrElse(
      logger.info(s"CreateAccount JSON is ${Json.toJson(userInfo)}", nino),
      ()
    )

    val timeContext: Timer.Context = metrics.nsiAccountCreationTimer.time()

    httpProxy.post(nsiCreateAccountUrl, userInfo, Map(nsiAuthHeaderKey → nsiBasicAuth))(nsiUserInfoFormat, hc.copy(authorization = None), ec)
      .map[SubmissionResult] { response ⇒
        val time = timeContext.stop()

        response.status match {
          case Status.CREATED ⇒
            logger.info(s"createAccount/insert returned 201 (Created) ${timeString(time)}", nino)
            SubmissionSuccess()

          case Status.CONFLICT ⇒
            logger.info(s"createAccount/insert returned 409 (Conflict) ${timeString(time)}", nino)
            SubmissionSuccess()

          case other ⇒
            handleErrorStatus(other, response, userInfo.nino, time)
        }
      }.recover {
        case e ⇒
          val time = timeContext.stop()
          metrics.nsiAccountCreationErrorCounter.inc()

          logger.warn(s"Encountered error while trying to create account ${timeString(time)}", e, nino)
          SubmissionFailure(None, "Encountered error while trying to create account", e.getMessage)
      }
  }

  override def updateEmail(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] = EitherT[Future, String, Unit]{
    val nino = userInfo.nino

    val timeContext: Timer.Context = metrics.nsiUpdateEmailTimer.time()

    httpProxy.put(nsiCreateAccountUrl, userInfo, true, Map(nsiAuthHeaderKey → nsiBasicAuth))(nsiUserInfoFormat, hc.copy(authorization = None), ec)
      .map[Either[String, Unit]] { response ⇒
        val time = timeContext.stop()

        response.status match {
          case Status.OK ⇒
            logger.info(s"createAccount/update returned 200 OK from NSI ${timeString(time)}", nino)
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

  override def healthCheck(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Unit] = EitherT[Future, String, Unit]{
    httpProxy.put(nsiCreateAccountUrl, userInfo, false, Map(nsiAuthHeaderKey → nsiBasicAuth))(nsiUserInfoFormat, hc.copy(authorization = None), ex)
      .map[Either[String, Unit]] { response ⇒
        response.status match {
          case Status.OK ⇒ Right(())
          case other     ⇒ Left(s"Received unexpected status $other from NS&I while trying to do health-check. Body was ${response.body}")
        }
      }.recover {
        case e ⇒ Left(s"Encountered error while trying to create account: ${e.getMessage}")
      }
  }

  private def handleErrorStatus(status: Int, response: HttpResponse, nino: NINO, time: Long) = {
    metrics.nsiAccountCreationErrorCounter.inc()

    status match {
      case Status.BAD_REQUEST ⇒
        logger.warn(s"Failed to create account as NSI, received status 400 (Bad Request) from NSI ${timeString(time)}", nino)
        handleBadRequestResponse(response)

      case Status.INTERNAL_SERVER_ERROR ⇒
        logger.warn(s"Failed to create account as NSI, received status 500 (Internal Server Error) from NSI ${timeString(time)}", nino)
        handleBadRequestResponse(response)

      case Status.SERVICE_UNAVAILABLE ⇒
        logger.warn(s"Failed to create account as NSI, received status 503 (Service Unavailable) from NSI ${timeString(time)}", nino)
        handleBadRequestResponse(response)

      case other ⇒
        logger.warn(s"Unexpected error during creating account, received status $other ${timeString(time)}", nino)
        SubmissionFailure(None, s"Something unexpected happened; response body: ${response.body}", other.toString)
    }
  }

  private def timeString(nanos: Long): String = s"(round-trip time: ${nanosToPrettyString(nanos)})"

  private def handleBadRequestResponse(response: HttpResponse): SubmissionFailure = {
    logger.warn(s"response body from NSI=${response.body}")
    response.parseJSON[SubmissionFailure](Some("error")) match {
      case Right(submissionFailure) ⇒ submissionFailure
      case Left(error)              ⇒ SubmissionFailure(None, "", error)
    }
  }
}
