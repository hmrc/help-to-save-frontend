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

import javax.inject.Singleton

import com.google.inject.Inject
import play.api.mvc._
import play.twirl.api.{Html, HtmlFormat}
import uk.gov.hmrc.helptosavefrontend.config._
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig._
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.helptosavefrontend.util.{Logging, toFuture, urlEncode}

import scala.concurrent.{ExecutionContext, Future}
import play.api.i18n.{I18nSupport, MessagesApi}

@Singleton
class HelpAndContactController @Inject() (val messagesApi:       MessagesApi,
                                          frontendAuthConnector: FrontendAuthConnector,
                                          metrics:               Metrics,
                                          formProvider:          PartialRetriever,
                                          http:                  WSHttp)(implicit ec: ExecutionContext)
  extends HelpToSaveAuth(frontendAuthConnector, metrics) with HelpToSaveFrontendController with I18nSupport with Logging {

  val TICKET_ID: String = "ticketId"

  val submitUrl: String = routes.HelpAndContactController.submitContactHmrcForm().url

  val contactHmrcFormPartialUrl: String = s"$contactBaseUrl/contact/contact-hmrc/form?service=$contactFormServiceIdentifier" +
    s"&submitUrl=${urlEncode(submitUrl)}"

  val contactHmrcSubmitPartialUrl: String = s"$contactBaseUrl/contact/contact-hmrc/form?resubmitUrl=${urlEncode(submitUrl)}"

  private def contactHmrcThankYouPartialUrl(ticketId: String) =
    s"$contactBaseUrl/contact/contact-hmrc/form/confirmation?ticketId=${urlEncode(ticketId)}"

  def getHelpAndContactPage: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext ⇒
    Ok(views.html.contact_hmrc(contactHmrcFormPartialUrl, None, formProvider))
  }(redirectOnLoginURL = routes.HelpAndContactController.getHelpAndContactPage().url)

  def submitContactHmrcForm: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext ⇒
    submitContactHmrc(contactHmrcSubmitPartialUrl,
                      routes.HelpAndContactController.contactHmrcThankYou(),
      body ⇒ views.html.contact_hmrc(contactHmrcFormPartialUrl, Some(body), formProvider))
  }(redirectOnLoginURL = routes.HelpAndContactController.getHelpAndContactPage().url)

  def contactHmrcThankYou: Action[AnyContent] = authorisedForHts { implicit request ⇒ implicit htsContext ⇒
    val ticketId = request.session.get(TICKET_ID).getOrElse("N/A")
    Ok(views.html.contact_hmrc_thankyou(contactHmrcThankYouPartialUrl(ticketId), formProvider))
  }(redirectOnLoginURL = routes.HelpAndContactController.getHelpAndContactPage().url)

  private def submitContactHmrc(formUrl:                         String,
                                successRedirect:                 Call,
                                failedValidationResponseContent: (Html) ⇒ HtmlFormat.Appendable)(
      implicit
      request: Request[AnyContent]): Future[Result] = {
    request.body.asFormUrlEncoded.fold[Future[Result]] {
      logger.warn("Trying to submit an empty contact form")
      internalServerError()
    }{ formData ⇒
      http.postForm(formUrl, formData.mapValues(_.toList))
        .map {
          resp ⇒
            resp.status match {
              case 200 ⇒ Redirect(successRedirect).withSession(request.session + (TICKET_ID -> resp.body))
              case 400 ⇒ BadRequest(failedValidationResponseContent(Html(resp.body)))
              case 500 ⇒ InternalServerError(Html(resp.body))
              case status ⇒
                logger.warn(s"Unexpected status code from contact HMRC form: $status")
                Status(status)(Html(resp.body))
            }
        }
    }
  }

}

