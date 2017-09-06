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

import com.typesafe.config.{ConfigException, ConfigFactory}
import org.scalatest.BeforeAndAfter
import play.api.{Configuration, Logger}
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.util.Toggles.FEATURE
import uk.gov.hmrc.play.test.UnitSpec

class TogglesSpec extends UnitSpec with TestSupport with BeforeAndAfter {

  def config(enabled: Boolean) = Configuration(ConfigFactory.parseString(
    s"""
       |feature-toggles{
       |  my-feature {
       |    enabled = ${if (enabled) "true" else "false"}
       |  }
       |}
    """.stripMargin))

  lazy val log = Logger("TogglesSpec")

  "A FEATURE" must {

    "must have an apply method which does not take a HList" in {
      val f1 = FEATURE("my-feature", config(true), log)
      f1.enabled shouldBe true

      val f2 = FEATURE("my-feature", config(false), log)
      f2.enabled shouldBe false
    }

    "be able to read a boolean from config" in {
      val f1 = FEATURE("my-feature", config(true), log)
      f1.enabled shouldBe true

      val f2 = FEATURE("my-feature", config(false), log)
      f2.enabled shouldBe false
    }

    "throw a runtime exception if the feature doesn't exist" in {
      a[ConfigException] shouldBe thrownBy(FEATURE("nonexistent", config(true), log))
    }

    "must have a method which performs one action if the boolean is true " +
      "and another if false using the accumulated data" in {
        val f1 = FEATURE("my-feature", config(true), log)
        val result1 = f1.thenOrElse("a", "b")
        result1 shouldBe "a"

        val f2 = FEATURE("my-feature", config(false), log)
        val result2 = f2.thenOrElse("a", "b")
        result2 shouldBe "b"
      }

  }
}

