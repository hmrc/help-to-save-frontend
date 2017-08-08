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
import play.api.Application
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.helptosavefrontend.connectors.SessionCacheConnector
import uk.gov.hmrc.helptosavefrontend.services.EnrolmentService
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.helptosavefrontend.util.toFuture

@Singleton
class UpdateEmailAddressController @Inject()(val sessionCacheConnector: SessionCacheConnector,
                                             val enrolmentService: EnrolmentService
                                            )(implicit app: Application, val messagesApi: MessagesApi)
  extends HelpToSaveAuth(app) with EnrolmentCheckBehaviour with SessionBehaviour with I18nSupport {

  def getUpdateYourEmailAddress: Action[AnyContent] = authorisedForHtsWithInfo {
    implicit request ⇒
      implicit htsContext ⇒
        checkIfAlreadyEnrolled { _ ⇒
          checkSession(
            SeeOther(routes.IntroductionController.getApply().url),
            _ ⇒ Ok(views.html.register.update_email_address())
          )

        }
  }

}
