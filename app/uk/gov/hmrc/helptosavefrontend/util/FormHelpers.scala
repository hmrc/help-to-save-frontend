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

package uk.gov.hmrc.helptosavefrontend.util

import play.api.data.Form

/**
  * Created by andy on 17/08/2017.
  */
object FormHelpers {
  def getErrorByKey[A](form: Option[Form[A]], errorKey: String) = {
    val either = for {
      theForm <- form.toRight("").right
      theError <- theForm.error(errorKey).toRight("").right
    } yield theError

    either match {
      case Left(_) => ""
      case Right(error) => error.message
    }
  }
}
