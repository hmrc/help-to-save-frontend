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

package uk.gov.hmrc.helptosavefrontend.forms

import java.util.function.Predicate

import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.data.Mapping

import scala.util.matching.Regex

@Singleton
class EmailValidation @Inject() (configuration: Configuration) {

  private val emailMaxLength: Int = configuration.underlying.getInt("email-validation.max-length")

  private val emailRegex: Regex = configuration.underlying.getString("email-validation.regex").r

  private val emailPredicate: Predicate[String] = emailRegex.pattern.asPredicate()

  val emailMapping: Mapping[String] = play.api.data.Forms.text(maxLength = emailMaxLength).verifying("error.email", emailPredicate.test(_))
}
