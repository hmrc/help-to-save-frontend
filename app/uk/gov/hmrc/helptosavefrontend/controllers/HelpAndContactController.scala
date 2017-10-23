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

package uk.gov.hmrc.helptosavefrontend.controllers

import java.net.URLEncoder
import javax.inject.Singleton

import com.google.inject.Inject
import play.api.Logger
import play.api.mvc._
import play.twirl.api.{Html, HtmlFormat}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext
import uk.gov.hmrc.helptosavefrontend.config._
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.helptosavefrontend.views

import scala.concurrent.{ExecutionContext, Future}
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import play.api.http.Status
import play.api.i18n.{I18nSupport, MessagesApi}
import uk.gov.hmrc.helptosavefrontend.util.Crypto

@Singleton
class HelpAndContactController @Inject() (val messagesApi:       MessagesApi,
                                          frontendAuthConnector: FrontendAuthConnector,
                                          metrics:               Metrics,
                                          formProvider:          FormPartialProvider,
                                          http:                  WSHttpExtension)(implicit ec: ExecutionContext, crypto: Crypto)
  extends HelpToSaveAuth(frontendAuthConnector, metrics) with FrontendController with ServicesConfig {

  val contactFrontendService: String = baseUrl("contact-frontend")

  val contactFormServiceIdentifier: String = "HTS"

  val TICKET_ID: String = "ticketId"

  private val submitUrl = routes.HelpAndContactController.submitContactHmrcForm().url

  private val contactHmrcFormPartialUrl = s"$contactFrontendService/contact/contact-hmrc/form?service=${contactFormServiceIdentifier}" +
    s"&submitUrl=${urlEncode(submitUrl)}"

  private lazy val contactHmrcSubmitPartialUrl = s"$contactFrontendService/contact/contact-hmrc/form?resubmitUrl=${urlEncode(submitUrl)}"

  private def contactHmrcThankYouPartialUrl(ticketId: String) =
    s"$contactFrontendService/contact/contact-hmrc/form/confirmation?ticketId=${urlEncode(ticketId)}"

  private def urlEncode(value: String) = URLEncoder.encode(value, "UTF-8")

  def getHelpAndContactPage: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    Future(Ok(views.html.contact_hmrc(contactHmrcFormPartialUrl, None, formProvider)))
  }(redirectOnLoginURL = FrontendAppConfig.ggLoginUrl)

  def submitContactHmrcForm: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    submitContactHmrc(contactHmrcSubmitPartialUrl,
                      routes.HelpAndContactController.contactHmrcThankYou(),
      (body: Html) ⇒ Future.successful(views.html.contact_hmrc(contactHmrcFormPartialUrl, Some(body), formProvider)))
  }(redirectOnLoginURL = FrontendAppConfig.ggLoginUrl)

  def contactHmrcThankYou: Action[AnyContent] = authorisedForHtsWithInfo { implicit request ⇒ implicit htsContext ⇒
    val ticketId = request.session.get(TICKET_ID).getOrElse("N/A")
    Future.successful(Ok(views.html.contact_hmrc_thankyou(contactHmrcThankYouPartialUrl(ticketId), formProvider)))
  }(redirectOnLoginURL = FrontendAppConfig.ggLoginUrl)

  private def submitContactHmrc(formUrl: String,
                                successRedirect: Call,
                                failedValidationResponseContent: (Html) ⇒ Future[HtmlFormat.Appendable])(
      implicit
      request: Request[AnyContent]): Future[Result] = {
    request.body.asFormUrlEncoded.map { formData ⇒
      http.postForm(formUrl, formData)
        .flatMap {
          resp ⇒
            resp.status match {
              case 200 ⇒ Future.successful(Redirect(successRedirect).withSession(request.session + (TICKET_ID -> resp.body)))
              case 400 ⇒ failedValidationResponseContent(Html(resp.body)).map(BadRequest(_))
              case 500 ⇒ Future.successful(InternalServerError(Html(resp.body)))
              case status ⇒
                Logger.warn(s"Unexpected status code from contact HMRC form: $status")
                Future.successful(Status(status)(Html(resp.body)))
            }
        }
    }.getOrElse {
      Logger.warn("Trying to submit an empty contact form")
      Future.successful(InternalServerError)
    }
  }

}

object PartialsFormReads {
  implicit val readPartialsForm: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    def read(method: String, url: String, response: HttpResponse) = response
  }
}
