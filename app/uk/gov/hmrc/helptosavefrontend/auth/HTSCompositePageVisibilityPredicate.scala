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

package uk.gov.hmrc.helptosavefrontend.auth

import java.net.{URI, URLEncoder}

import uk.gov.hmrc.helptosavefrontend.FrontendAppConfig._
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel.L200
import uk.gov.hmrc.play.frontend.auth.{CompositePageVisibilityPredicate, PageVisibilityPredicate}

object HTSCompositePageVisibilityPredicate extends CompositePageVisibilityPredicate {
  override def children: Seq[PageVisibilityPredicate] = Seq(
    new HTSStrongCredentialPredicate(twoFactorURI),
    new UpliftingIdentityConfidencePredicate(L200, ivUpliftURI)
  )

  private val ivUpliftURI: URI =
    new URI(s"$ivUpliftUrl?origin=$sosOrigin&" +
      s"completionURL=${URLEncoder.encode(loginCallbackURL, "UTF-8")}&" +
      s"failureURL=${URLEncoder.encode(perTaxIdentityCheckFailedUrl, "UTF-8")}" +
      s"&confidenceLevel=200")

  private val twoFactorURI: URI =
    new URI(s"$twoFactorUrl?" +
      s"continue=${URLEncoder.encode(loginCallbackURL, "UTF-8")}&" +
      s"failure=${URLEncoder.encode(perTaxIdentityCheckFailedUrl, "UTF-8")}")
}
