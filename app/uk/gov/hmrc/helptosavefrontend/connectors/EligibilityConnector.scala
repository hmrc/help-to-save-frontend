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

package uk.gov.hmrc.helptosavefrontend.connectors

import com.google.inject.{ImplementedBy, Singleton}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.models.EligibilityResult
import uk.gov.hmrc.helptosavefrontend.util._
import uk.gov.hmrc.play.config.ServicesConfig

@ImplementedBy(classOf[EligibilityConnectorImpl])
trait EligibilityConnector {
  def checkEligibility(nino: String)(implicit hc: HeaderCarrier): Result[EligibilityResult]
}

@Singleton
class EligibilityConnectorImpl extends EligibilityConnector with ServicesConfig {

  private val helpToSaveEligibilityURL: String = baseUrl("help-to-save-eligibility")

  // TODO: read from config?
  private def serviceURL(nino: String) = s"help-to-save-eligibility-check/eligibilitycheck/$nino"

  override def checkEligibility(nino: String)(implicit hc: HeaderCarrier): Result[EligibilityResult] =
    getResult(s"$helpToSaveEligibilityURL/${serviceURL(nino)}")

}
