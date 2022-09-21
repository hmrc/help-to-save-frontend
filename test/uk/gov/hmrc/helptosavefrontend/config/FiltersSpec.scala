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

package uk.gov.hmrc.helptosavefrontend.config

import akka.stream.ActorMaterializer
import com.kenshoo.play.metrics.MetricsFilter
import play.api.Configuration
import play.api.http.DefaultHttpFilters
import play.api.mvc.EssentialFilter
import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter
import uk.gov.hmrc.helptosavefrontend.controllers.ControllerSpecWithGuiceAppPerTest
import uk.gov.hmrc.integration.servicemanager.ServiceManagerClient.system
import uk.gov.hmrc.play.bootstrap.filters._
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.SessionCookieCryptoFilter
import uk.gov.hmrc.play.bootstrap.frontend.filters.deviceid.DeviceIdFilter
import uk.gov.hmrc.play.bootstrap.frontend.filters.{FrontendFilters, _}

class FiltersSpec extends ControllerSpecWithGuiceAppPerTest {
  val frontendFilters = new DefaultHttpFilters()
  val allowListFilter: AllowListFilter = mock[AllowListFilter]

  "Filters" must {

    "include the allowList filter if the allowList from config is non empty" in {
      val config = Configuration("http-header-ip-allowlist" → List("1.2.3"))

      val filters = new Filters(config, allowListFilter, frontendFilters)
      filters.filters shouldBe Seq(allowListFilter)
    }

    "not include the allowList filter if the allowList from config is empty" in {
      val config = Configuration("http-header-ip-allowlist" → List())

      val filters = new Filters(config, allowListFilter, frontendFilters)
      filters.filters shouldBe Seq()
    }
  }

}
