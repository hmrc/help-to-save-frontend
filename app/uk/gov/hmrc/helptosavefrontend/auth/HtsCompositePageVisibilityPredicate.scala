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

import play.api.mvc.Results.Redirect
import uk.gov.hmrc.helptosavefrontend.FrontendAppConfig._
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel.L200
import uk.gov.hmrc.play.frontend.auth.{CompositePageVisibilityPredicate, IdentityConfidencePredicate, PageVisibilityPredicate}

import scala.concurrent.Future

object HtsCompositePageVisibilityPredicate extends CompositePageVisibilityPredicate {
  override def children: Seq[PageVisibilityPredicate] = Seq(
    new HtsStrongCredentialPredicate(twoFactorURI),
    new UpliftingIdentityConfidencePredicate(L200, ivUpliftURI)
  )

  val ivUpliftURI: URI =
    new URI(s"$ivUpliftUrl?origin=$sosOrigin&" +
      s"completionURL=${URLEncoder.encode(IdentityCallbackUrl, "UTF-8")}&" +
      s"failureURL=${URLEncoder.encode(IdentityCallbackUrl, "UTF-8")}" +
      s"&confidenceLevel=200")

  val twoFactorURI: URI =
    new URI(s"$twoFactorUrl?" +
      s"continue=${URLEncoder.encode(HtsDeclarationUrl, "UTF-8")}&" +
      s"failure=${URLEncoder.encode(TwoFactorFailedUrl, "UTF-8")}")

  class UpliftingIdentityConfidencePredicate(requiredConfidenceLevel: ConfidenceLevel, upliftConfidenceUri: URI)
    extends IdentityConfidencePredicate(requiredConfidenceLevel, Future.successful(Redirect(upliftConfidenceUri.toString)))

}
