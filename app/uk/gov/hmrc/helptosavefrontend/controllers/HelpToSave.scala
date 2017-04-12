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

import java.time.LocalDate

import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.mvc._
import uk.gov.hmrc.helptosavefrontend.models.{ContactPreference, UserDetails}
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

object HelpToSave extends HelpToSave

trait HelpToSave extends FrontendController {

  val user =
    UserDetails("Bob",
      "abcdefg",
      LocalDate.ofEpochDay(0L),
      "bob@email.com",
      "01234567890",
      List("1 the Street, Happy Town, AB1 B23"),
      ContactPreference.Email
     )

  val helpToSave = Action.async { implicit request ⇒
		Future.successful(Ok(uk.gov.hmrc.helptosavefrontend.views.html.register.declaration(user)))
  }

  val notElgibible = Action.async { implicit request ⇒
    Future.successful(Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.not_eligibile()))
  }

  val start = Action.async{ implicit request ⇒
    Future.successful(Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.start()))
  }


}
