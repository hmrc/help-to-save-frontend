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

package hts.driver

import java.util.concurrent.TimeUnit

import org.apache.commons.lang3.StringUtils
import cats.syntax.either._
import org.openqa.selenium.WebDriver
import hts.driver.Browser._

object Driver {
  val systemProperties = System.getProperties

  val webDriver: Either[String,WebDriver] = {
    val selectedDriver: Either[String,WebDriver] = Option(systemProperties.getProperty("browser")).map(_.toLowerCase) match {
        case Some("firefox")                  ⇒ Right(createFirefoxDriver())
        case Some("chrome")                   ⇒ Right(createChromeDriver())
        case Some("phantomjs")                ⇒ Right(createPhantomJsDriver())
        case Some("gecko")                    ⇒ Right(createGeckoDriver())
        case Some(other)                      ⇒ Left(s"Unrecognised browser: $other")
        case None                             ⇒ Left("No browser set")
      }

    selectedDriver.map{ driver ⇒
      sys.addShutdownHook(driver.quit())
      driver.manage().timeouts().implicitlyWait(0, TimeUnit.SECONDS)
    }
    selectedDriver
  }
}
