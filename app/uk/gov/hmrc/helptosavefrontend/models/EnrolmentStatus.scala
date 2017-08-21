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

package uk.gov.hmrc.helptosavefrontend.models

import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}

sealed trait EnrolmentStatus {
  def fold[T](ifNotEnrolled: ⇒ T, ifEnrolled: Boolean ⇒ T): T = this match {
    case e: Enrolled  ⇒ ifEnrolled(e.itmpHtSFlag)
    case NotEnrolled  ⇒ ifNotEnrolled
  }
}

object EnrolmentStatus {
  case class Enrolled(itmpHtSFlag: Boolean) extends EnrolmentStatus

  case object NotEnrolled extends EnrolmentStatus

  implicit val enrolmentStatusFormat: Reads[EnrolmentStatus] = new Reads[EnrolmentStatus]{
    case class EnrolledJSON (enrolled: Boolean = true, itmpHtSFlag: Boolean)
    case class NotEnrolledJSON (enrolled: Boolean = false)

    implicit val enrolledFormat: Reads[EnrolledJSON] = Json.reads[EnrolledJSON]
    implicit val notEnrolledFormat: Reads[NotEnrolledJSON] = Json.reads[NotEnrolledJSON]

    override def reads(json: JsValue): JsResult[EnrolmentStatus] =
    // take advantage of the fact that EnrolledJSON has a non-optional field
    // which NotEnrolledJSON so that we can detect when we have one or the other
      Json.fromJson[EnrolledJSON](json).fold(
        { e1 ⇒
          Json.fromJson[NotEnrolledJSON](json).fold(
            e2          ⇒ JsError(e1 ++ e2),
            notEnrolled ⇒ JsSuccess(NotEnrolled)
          )
        },
        enrolled ⇒ JsSuccess(Enrolled(enrolled.itmpHtSFlag))
      )

  }

}


