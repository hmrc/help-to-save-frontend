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

import java.time.LocalDate

import com.google.inject.ImplementedBy
import uk.gov.hmrc.helptosavefrontend.WSHttp
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import javax.inject.Singleton

import play.api.Logger
import play.api.http.Status

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class SubmissionSuccess()

case class SubmissionError(code: Int, message: String)

@ImplementedBy(classOf[NSAndIConnectorImpl])
trait NSAndIConnector {
  type NSAndIResponse = Either[SubmissionError, SubmissionSuccess]

  def createAccount(userINfo: NSIUserInfo)(implicit hc: HeaderCarrier): Future[NSAndIResponse]

  val nSAndIUrl: String
  val http: HttpGet with HttpPost
}

@Singleton
class NSAndIConnectorImpl extends NSAndIConnector with ServicesConfig {

  override val nSAndIUrl: String = baseUrl("ns-and-i")
   val nSAndIUrlENd: String =  getString("microservice.services.ns-and-i.url")
  override val http: HttpGet with HttpPost = WSHttp

  override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier): Future[NSAndIResponse] = {
    //todo add in auth header when we get the right critria from ns and I ¯\_(ツ)_/¯
    http.POST[NSIUserInfo, HttpResponse](s"$nSAndIUrl/$nSAndIUrlENd", userInfo,
      headers = Seq(("Authorization1","Testing123"))).map { r =>
      Right(SubmissionSuccess())
    }.recover {
      case ex: Throwable => onError(ex)
    }
  }

  private def onError(ex: Throwable) = {
    val (code, message) = ex match {
      case e: HttpException => (e.responseCode, e.getMessage)

      case e: Upstream4xxResponse => (e.reportAs, e.getMessage)
      case e: Upstream5xxResponse => (e.reportAs, e.getMessage)

      case e: Throwable => (Status.INTERNAL_SERVER_ERROR, e.getMessage)
    }

    Logger.error(s"Failure from NS and I, code $code and body $message")
    Left(SubmissionError(code, message))
  }
}
