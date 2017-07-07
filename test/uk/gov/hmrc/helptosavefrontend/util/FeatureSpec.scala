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
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import shapeless.HNil
import uk.gov.hmrc.helptosavefrontend.util.FEATURE.LogLevel

class FeatureSpec extends WordSpec with Matchers{

  def config(enabled: Boolean) = Configuration(ConfigFactory.parseString(
    s"""
       |feature-toggles{
       |  my-feature {
       |    enabled = ${if(enabled) "true" else "false"}
       |  }
       |}
    """.stripMargin))

  class LogContext {
    var lastLogMessage: Option[(LogLevel,String)] = None

    def log(level: LogLevel, message: String, error: Option[Throwable] = None): Unit = lastLogMessage = Some(level → message)
  }

  "A FEATURE" must {

    "must have an apply method which does not take a HList" in new LogContext{
      val f1 = FEATURE("my-feature", config(true), log _)
      f1.enabled shouldBe true

      val f2 = FEATURE("my-feature", config(false), log _)
      f2.enabled shouldBe false
    }


    "be able to read a boolean from config" in new LogContext{
      val f1 = FEATURE("my-feature", config(true), log _)
      f1.enabled shouldBe true

      val f2 = FEATURE("my-feature", config(false), log _)
      f2.enabled shouldBe false
    }

    "throw a runtime exception if the feature doesn't exist" in new LogContext{
      a[ConfigException] shouldBe thrownBy(FEATURE("nonexistent", config(true), log _))
    }

    "must have a method which performs one action if the boolean is true " +
      "and another if false using the accumulated data" in new LogContext{
      val f1 = FEATURE("my-feature", config(true), log _)
      val result1= f1.thenOrElse("a","b")
      result1 shouldBe "a"

      val f2 = FEATURE("my-feature", config(false), log _)
      val result2 = f2.thenOrElse("a", "b")
      result2 shouldBe "b"
    }


    def testLogging(b: Boolean, logContext: LogContext): Unit = {
      val regex = s"""Feature my-feature \\(enabled: $b\\) executed in \\d+ ns""".r

      val f = FEATURE("my-feature", config(b), logContext.log _)

      logContext.lastLogMessage.isEmpty shouldBe true
      f.thenOrElse((),())

      logContext.lastLogMessage.isEmpty shouldBe false
      logContext.lastLogMessage.get._1 shouldBe LogLevel.INFO

      regex.pattern.matcher(logContext.lastLogMessage.get._2).matches() shouldBe true
    }

    "must log elapsed time when executing a feature that is enabled" in new LogContext {
      testLogging(true, this)
    }


    "must log elapsed time when executing a feature that is disabled" in new LogContext {
      testLogging(false, this)
    }

  }
}
