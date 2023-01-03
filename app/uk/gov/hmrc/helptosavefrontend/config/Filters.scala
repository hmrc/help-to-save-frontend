/*
 * Copyright 2023 HM Revenue & Customs
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

import com.google.inject.{Inject, Singleton}
import configs.syntax._
import play.api.Configuration
import play.api.http.HttpFilters
import play.api.mvc.EssentialFilter

@Singleton
class Filters @Inject() (
                          configuration: Configuration,
                          AllowListFilter: AllowListFilter,
                          frontendFilters: HttpFilters
) extends HttpFilters {

  val allowListFilterEnabled: Boolean =
    configuration.underlying.get[List[String]]("http-header-ip-allowlist").value.nonEmpty

  override val filters: Seq[EssentialFilter] =
    if (allowListFilterEnabled) {
      frontendFilters.filters :+ AllowListFilter
    } else {
      frontendFilters.filters
    }

}
