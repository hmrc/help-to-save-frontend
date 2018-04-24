/*
 * Copyright 2018 HM Revenue & Customs
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

import akka.stream.Materializer
import com.kenshoo.play.metrics.MetricsFilter
import play.api.Configuration
import play.api.mvc.EssentialFilter
import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.play.bootstrap.filters.frontend.{FrontendAuditFilter, HeadersFilter, SessionTimeoutFilter}
import uk.gov.hmrc.play.bootstrap.filters.frontend.crypto.CookieCryptoFilter
import uk.gov.hmrc.play.bootstrap.filters.frontend.deviceid.DeviceIdFilter
import uk.gov.hmrc.play.bootstrap.filters.{CacheControlConfig, CacheControlFilter, FrontendFilters, LoggingFilter}

class FiltersSpec extends TestSupport {

  // can't use scalamock for CacheControlFilter since a logging statement during class
  // construction requires a parameter from the CacheControlConfig. Using scalamock
  // reuslts in a NullPointerException since no CacheControlConfig is there
  val mockCacheControlerFilter = new CacheControlFilter(CacheControlConfig(), mock[Materializer])

  class TestableFrontendFilters extends FrontendFilters(
    stub[Configuration],
    stub[LoggingFilter],
    stub[HeadersFilter],
    stub[SecurityHeadersFilter],
    stub[FrontendAuditFilter],
    stub[MetricsFilter],
    stub[DeviceIdFilter],
    stub[CSRFFilter],
    stub[CookieCryptoFilter],
    stub[SessionTimeoutFilter],
    mockCacheControlerFilter
  ) {
    override lazy val enableSecurityHeaderFilter: Boolean = false
    override val filters: Seq[EssentialFilter] = Seq()
  }

  val frontendFilters = new TestableFrontendFilters
  val whiteListFilter = mock[WhitelistFilter]

  "Filters" must {

    "include the whitelist filter if the whitelist from config is non empty" in {
      val config = Configuration("http-header-ip-whitelist" → List("1.2.3"))

      val filters = new Filters(config, whiteListFilter, frontendFilters)
      filters.filters shouldBe Seq(whiteListFilter)
    }

    "not include the whitelist filter if the whitelist from config is empty" in {
      val config = Configuration("http-header-ip-whitelist" → List())

      val filters = new Filters(config, whiteListFilter, frontendFilters)
      filters.filters shouldBe Seq()
    }
  }

}
