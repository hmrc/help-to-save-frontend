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

import play.api.mvc.Results._
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosavefrontend.views.html.introduction.about_help_to_save
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future


trait EmailVerificationController {

  def showSuccess(): Action[AnyContent]  = Action.async {
    implicit request =>
      Future.successful(Ok("hello"))
  }



}


object EmailVerificationController extends EmailVerificationController {

}
