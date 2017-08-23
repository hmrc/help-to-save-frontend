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

package hts.steps

import java.util.concurrent.TimeUnit

import cats.syntax.either._
import cucumber.api.scala.{EN, ScalaDsl}
import hts.driver.Driver
import org.openqa.selenium.WebDriver
import org.scalatest.Matchers


trait Steps extends ScalaDsl with EN with Matchers {

  import Steps._

  /** Tries to get the value of [[_driver]] - will throw an exception if it doesn't exist */
  implicit def driver: WebDriver = _driver.getOrElse(sys.error("Driver does not exist"))

  // create a new driver for each scenario
  Before { _ ⇒
    if (_driver.isEmpty) {
      val d = Driver.newWebDriver()
        // map the left to Nothing
        .leftMap(e ⇒ sys.error(s"Could not find driver: $e"))
        // merge will merge Nothing and WebDriver to WebDriver since Nothing is a subtype of everything
        .merge
      d.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS)
      _driver = Some(d)
    }
  }

  After { _ ⇒
    _driver.foreach(_.quit())
    _driver = None
  }


}

object Steps {

  /**
    * Each step definition file extends the `Steps` trait , but they will all reference this single driver
    * in the companion object. Having this variable in the trait would cause multiple drivers to be
    * created
    */
  private var _driver: Option[WebDriver] = None

}