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

package uk.gov.hmrc.helptosavefrontend.models

import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.models.HTSSession.EligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResult.{Eligible, Ineligible}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.UserInfo
import uk.gov.hmrc.helptosavefrontend.util.Email

/**
 * Session data for the HTS journey
 *
 * @param eligibilityCheckResult Contains `Some` if the user has gone through the eligibility checks
 *                               and they are eligible for HtS and contains `None` if the user has gone
 *                               through eligibility checks and they are ineligible
 * @param confirmedEmail         Contains `Some` if the user has confirmed their email address and `None`
 *                               if they haven't
 */
case class HTSSession(eligibilityCheckResult: Option[Either[Ineligible, EligibleWithUserInfo]],
                      confirmedEmail:         Option[Email],
                      pendingEmail:           Option[Email],
                      ivURL:                  Option[String]                                   = None,
                      ivSuccessURL:           Option[String]                                   = None
)

object HTSSession {

  case class EligibleWithUserInfo(eligible: Eligible, userInfo: UserInfo)

  object EligibleWithUserInfo {
    implicit val format: Format[EligibleWithUserInfo] = Json.format[EligibleWithUserInfo]
  }

  implicit def eitherFormat[A, B](implicit aFormat: Format[A], bFormat: Format[B]): Format[Either[A, B]] =
    new Format[Either[A, B]] {
      override def reads(json: JsValue): JsResult[Either[A, B]] =
        (json \ "l").validate[A].map[Either[A, B]](Left(_))
          .orElse((json \ "r").validate[B].map(Right(_)))

      override def writes(o: Either[A, B]): JsValue =
        o.fold(
          a ⇒ JsObject(Seq("l" → Json.toJson(a))),
          b ⇒ JsObject(Seq("r" → Json.toJson(b)))
        )
    }

  implicit val htsSessionFormat: Format[HTSSession] = Json.format[HTSSession]
}
