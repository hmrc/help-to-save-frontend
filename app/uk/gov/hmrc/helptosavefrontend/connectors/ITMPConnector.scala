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
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Format, Json}
import play.mvc.Http.Status.{CONFLICT, OK}
import uk.gov.hmrc.helptosavefrontend.config.{WSHttp, WSHttpExtension}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.itmpEnrolmentURL
import uk.gov.hmrc.helptosavefrontend.util.{Logging, NINO, Result}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

@ImplementedBy(classOf[ITMPConnectorImpl])
trait ITMPConnector {

  def setFlag(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

}

@Singleton
class ITMPConnectorImpl @Inject()(http: WSHttp) extends ITMPConnector with Logging {
  import uk.gov.hmrc.helptosavefrontend.connectors.ITMPConnectorImpl._

  def url(nino: NINO): String = s"$itmpEnrolmentURL/set-enrolment-flag/$nino"

  override def setFlag(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    EitherT(http.post(url(nino), PostBody())
      .map{ response ⇒
        response.status match {
          case OK ⇒ Right(())
          case CONFLICT ⇒
            logger.warn("Tried to set ITMP HtS flag even though it was already set - proceeding as normal")
            Right(())
          case other ⇒
            Left(s"Received unexpected response status ($other) when trying to set ITMP flag")
        }
      }
      .recover{
        case NonFatal(e) ⇒
          Left(s"Encountered unexpected error while trying to set the ITMP flag: ${e.getMessage}")
      })

}


object ITMPConnectorImpl {

  /** The information required in the body of the post request to set the ITMP flag */
  // TODO: put in the actual required details when they are known
  private[connectors] case class PostBody(tmp: String = "")

  implicit val postBodyFormat: Format[PostBody] = Json.format[PostBody]

}
