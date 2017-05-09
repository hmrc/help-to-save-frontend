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

package uk.gov.hmrc.helptosavefrontend

import java.util.UUID

import akka.util.ByteString
import com.google.inject.{Inject, Singleton}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.mvc._
import play.api.{Application, Configuration, Play}
import play.twirl.api.Html
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.play.audit.filters.FrontendAuditFilter
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.frontend.bootstrap.DefaultFrontendGlobal
import uk.gov.hmrc.play.http.logging.filters.FrontendLoggingFilter
import uk.gov.hmrc.play.filters.MicroserviceFilterSupport
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import play.api.libs.streams.Accumulator
import uk.gov.hmrc.helptosavefrontend.controllers.routes

import scala.concurrent.ExecutionContext

object FrontendGlobal
  extends DefaultFrontendGlobal {

  override val auditConnector = FrontendAuditConnector
  override val loggingFilter = LoggingFilter
  override val frontendAuditFilter = AuditFilter
  lazy val sessionFilter =
    new SessionFilter(Results.Redirect(routes.StartPagesController.getAboutHelpToSave()))

  override def onStart(app: Application) {
    super.onStart(app)
    ApplicationCrypto.verifyConfiguration()
  }

  override def filters = super.filters :+ sessionFilter

  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit rh: Request[_]): Html =
    uk.gov.hmrc.helptosavefrontend.views.html.error_template(pageTitle, heading, message)

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")
}

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object LoggingFilter extends FrontendLoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object AuditFilter extends FrontendAuditFilter with RunMode with AppName with MicroserviceFilterSupport {

  override lazy val maskedFormFields = Seq("password")

  override lazy val applicationPort = None

  override lazy val auditConnector = FrontendAuditConnector

  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}

/**
  * Checks if the request has cookie with a HTS session ID in it - if not, the filter creates one and
  * performs `whenNoSession` with the new session ID in it
  */
@Singleton
class SessionFilter[A](whenNoSession: => Result)(implicit app: Application) extends EssentialFilter {

  implicit val ex = app.injector.instanceOf[ExecutionContext]
  val sessionIdKey = app.configuration.underlying.getString("microservice.services.keystore.session-key")

  private def createHtsCookie() = Cookie(name = sessionIdKey, value = s"hts-session-${UUID.randomUUID}")

  override def apply(next: EssentialAction): EssentialAction = new EssentialAction {
    override def apply(requestHeader: RequestHeader): Accumulator[ByteString, Result] = {
      next(requestHeader).map{ response =>
        requestHeader.cookies.find(_.name == sessionIdKey).fold(
          whenNoSession.withCookies(createHtsCookie())
        ) { _ => response }
      }
    }
  }
}