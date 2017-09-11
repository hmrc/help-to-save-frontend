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

package uk.gov.hmrc.helptosavefrontend.audit

import javax.inject.{Inject, Singleton}

import uk.gov.hmrc.helptosavefrontend.config.FrontendAuditConnector
import uk.gov.hmrc.helptosavefrontend.models.HTSEvent
import uk.gov.hmrc.helptosavefrontend.util.Logging
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class HTSAuditor @Inject() () extends Logging {
  val auditConnector: AuditConnector = FrontendAuditConnector

  def sendEvent(event: HTSEvent): Unit = {
    val checkEventResult = auditConnector.sendEvent(event.value)
    checkEventResult.onFailure {
      case e: Throwable ⇒ logger.error(s"Unable to post audit event of type ${event.value.auditType} to audit connector - ${e.getMessage}", e)
    }
  }
}
