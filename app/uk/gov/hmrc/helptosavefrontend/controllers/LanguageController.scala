/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.Inject
import play.api.Logger
import play.api.i18n.{I18nSupport, Lang}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.controllers.LanguageController._
import uk.gov.hmrc.play.language.LanguageUtils

class LanguageController @Inject() (cpd:          CommonPlayDependencies,
                                    mcc:          MessagesControllerComponents,
                                    errorHandler: ErrorHandler)(
    implicit
    val frontendAppConfig: FrontendAppConfig) extends BaseController(cpd, mcc, errorHandler) with I18nSupport {

  def switchToEnglish: Action[AnyContent] = switchToLang(english)

  def switchToWelsh: Action[AnyContent] = {
    switchToLang(welsh)
  }

  private def switchToLang(lang: Lang) = Action { implicit request ⇒
    val newLang = if (frontendAppConfig.enableLanguageSwitching) lang else english

    request.headers.get(REFERER) match {
      case Some(referrer) ⇒ {
        Redirect(referrer).withLang(newLang).flashing(LanguageUtils.FlashWithSwitchIndicator)
      }
      case None ⇒
        Logger.warn("Unable to get the referrer, so sending them to the start.")
        Redirect(frontendAppConfig.checkEligibilityUrl).withLang(newLang)
    }
  }

}

object LanguageController {
  val english: Lang = Lang("en")
  val welsh: Lang = Lang("cy")
}
