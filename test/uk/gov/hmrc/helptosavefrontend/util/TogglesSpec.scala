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

import org.scalatest.BeforeAndAfter
import play.api.{Configuration, Logger}
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.play.test.UnitSpec

class TogglesSpec extends UnitSpec with TestSupport with BeforeAndAfter {

  import Toggles._

  val mockConfiguration = mock[Configuration]

  "toggles" must {
    "when testing find enabled in the config" in {
      (mockConfiguration.getBoolean(_: String)).expects("feature-toggles.test-feature0.enabled").returning(Some(true))
      val ftr = FEATURE("test-feature0", mockConfiguration, "")
      ftr.enabled() shouldBe FEATURE_THEN("test-feature0", true, Some(""), ftr.logger)
    }

    "when testing find not enabled in the config" in {
      (mockConfiguration.getBoolean(_: String)).expects("feature-toggles.test-feature1.enabled").returning(Some(false))
      val ftr = FEATURE("test-feature1", mockConfiguration, "")
      ftr.enabled() shouldBe FEATURE_THEN("test-feature1", false, Some(""), ftr.logger)
    }

    "when testing find misconfigured config" in {
      (mockConfiguration.getBoolean(_: String)).expects("feature-toggles.test-feature2.enabled").returning(None)
      val ftr = FEATURE("test-feature2", mockConfiguration, "")
      an[Exception] should be thrownBy ftr.enabled()
    }

    "given a FEATURE_THEN that is enabled and has an action, the action is executed" in {
      val ftrThen = FEATURE_THEN[Int]("test-feature0", true, Some(0), Logger("test-feature0"))

        def action = 1

      val result = ftrThen.thenDo {
        action
      }
      result shouldBe Right(1)
    }

    "given a FEATURE_THEN that is not enabled and has an action, the action is executed" in {
      val ftrThen = FEATURE_THEN[Int]("test-feature0", false, Some(0), Logger("test-feature0"))

        def action = 1

      val result = ftrThen.thenDo {
        action
      }
      result shouldBe Left(Some(0))
    }

    "given a FEATURE_THEN that is not enabled and has an action, the action is executed even if the unconfiguredVal is None" in {
      val ftrThen = FEATURE_THEN[Int]("test-feature0", true, None, Logger("test-feature0"))

        def action = 1

      val result = ftrThen.thenDo {
        action
      }
      result shouldBe Right(1)
    }

    "given a FEATURE, the enabled and thenDo should be able to obtain an Either" in {
      (mockConfiguration.getBoolean(_: String)).expects("feature-toggles.test-feature0.enabled").returning(Some(true))

        def action = 1

      val result = FEATURE("test-feature0", mockConfiguration) enabled () thenDo {
        action
      }
      result shouldBe Right(1)
    }

    "throw an exception if the feature is not configured and no unconfigured value has been defined" in {

      (mockConfiguration.getBoolean(_: String)).expects("feature-toggles.test-feature1.enabled").returning(Some(false))
      var x: Int = 0

        def action = 1

      an[Exception] shouldBe thrownBy(x = FEATURE("test-feature1", mockConfiguration) enabled () thenDo {
        action
      })
    }

    "there is an implicit conversion that converts an Either[Option[A], A] to an A that works if the feature is configured" in {
      (mockConfiguration.getBoolean(_: String)).expects("feature-toggles.test-feature1.enabled").returning(Some(false))

        def action = 1

      val result: Int = FEATURE("test-feature1", mockConfiguration, 0) enabled () thenDo {
        action
      }
      result shouldBe 0
    }

    "There is an implicit conversion function that allows the unconfigured value to be returned if the feature is unconfigured and the unconfigured value is defined" in {
      (mockConfiguration.getBoolean(_: String)).expects("feature-toggles.test-feature1.enabled").returning(Some(false))

        def action = 1

      val result: Int = FEATURE("test-feature1", mockConfiguration, 0) enabled () thenDo {
        action
      }
      result shouldBe 0
    }

    "side effect type actions are possible" in {
      (mockConfiguration.getBoolean(_: String)).expects("feature-toggles.test-feature0.enabled").returning(Some(true))
      var result = 0
      FEATURE("test-feature0", mockConfiguration) enabled () thenDo {
        result = 1
      }
      result shouldBe 1
    }

    "otherwise can be applied to an Right value" in {
      val r = Right(0)
      val result: Int = r.otherwise {
        10
      }
      result shouldBe 0
    }

    "otherwise can be applied to a Left value" in {
      val r = Left(0)
      val result: Int = r.otherwise {
        10
      }
      result shouldBe 10
    }

    "it is possible to use otherwise to execute the configured branch in the form of a DSL" in {
      (mockConfiguration.getBoolean(_: String)).expects("feature-toggles.test-feature0.enabled").returning(Some(true))
      val result = FEATURE("test-feature0", mockConfiguration) enabled () thenDo {
        10
      } otherwise {
        11
      }
      result shouldBe 10
    }

    "it is possible to use otherwise to execute the unconfigured branch in the form of a DSL" in {
      (mockConfiguration.getBoolean(_: String)).expects("feature-toggles.test-feature1.enabled").returning(Some(false))
      val result = FEATURE("test-feature1", mockConfiguration) enabled () thenDo {
        10
      } otherwise {
        11
      }
      result shouldBe 11
    }

    "side effect type actions are possible with an otherwise executing the configured branch" in {
      (mockConfiguration.getBoolean(_: String)).expects("feature-toggles.test-feature0.enabled").returning(Some(true))
      var result = 0
      FEATURE("test-feature0", mockConfiguration) enabled () thenDo {
        result = 1
      } otherwise {
        result = 2
      }
      result shouldBe 1
    }

    "side effect type actions are possible with an otherwise executing the unconfigured branch" in {
      (mockConfiguration.getBoolean(_: String)).expects("feature-toggles.test-feature1.enabled").returning(Some(false))
      var result = 0
      FEATURE("test-feature1", mockConfiguration) enabled () thenDo {
        result = 1
      } otherwise {
        result = 2
      }
      result shouldBe 2
    }
  }
}

