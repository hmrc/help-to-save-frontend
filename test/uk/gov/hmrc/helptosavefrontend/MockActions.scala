/*
 * Copyright 2022 HM Revenue & Customs
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

import com.codahale.metrics.{Counter, Timer}
import com.kenshoo.play.metrics.{Metrics => PlayMetrics}
import org.scalamock.scalatest.MockFactory
import play.api.Configuration
import uk.gov.hmrc.helptosavefrontend.forms.EmailValidation
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics

trait MockActions extends MockFactory {

  val mockMetrics = new Metrics(stub[PlayMetrics]) {
    override def timer(name: String): Timer = new Timer()

    override def counter(name: String): Counter = new Counter()
  }

  implicit val mockEmailValidation: EmailValidation =
    new EmailValidation(
      Configuration(
        "email-validation.max-total-length" → Int.MaxValue,
        "email-validation.max-local-length" → Int.MaxValue,
        "email-validation.max-domain-length" → Int.MaxValue
      )
    )

}
