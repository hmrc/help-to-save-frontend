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

package uk.gov.hmrc.helptosavefrontend.util

import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration

class NINOLogMessageTransformerImplSpec extends WordSpec with Matchers {

  "NINOLogMessageTransformerImpl" must {

    "attach the NINO in the prefix of a message if configured" in {
      val transformer = new NINOLogMessageTransformerImpl(Configuration("nino-logging.enabled" → true))
      transformer.transform("Hello", "NINO") shouldBe "For NINO [NINO]: Hello"
    }

    "not attach the NINO in the prefix of a message if not configured" in {
      val transformer = new NINOLogMessageTransformerImpl(Configuration("nino-logging.enabled" → false))
      transformer.transform("Hello", "NINO") shouldBe "Hello"
    }

  }

}
