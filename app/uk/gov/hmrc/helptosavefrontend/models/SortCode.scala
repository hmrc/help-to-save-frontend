/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.libs.json.{JsError, JsString, JsSuccess, Reads, Writes}

import scala.util.Try

case class SortCode(digit1: Int, digit2: Int, digit3: Int, digit4: Int, digit5: Int, digit6: Int) {
  override def toString: String = s"$digit1$digit2$digit3$digit4$digit5$digit6"
}

object SortCode {
  def apply(digits: Seq[Int]): Option[SortCode] = digits.toList match {
    case d1 :: d2 :: d3 :: d4 :: d5 :: d6 :: Nil => Some(SortCode(d1, d2, d3, d4, d5, d6))
    case _                                       => None
  }

  implicit val writes: Writes[SortCode] = Writes[SortCode](s => JsString(s.toString))

  implicit val reads: Reads[SortCode] = Reads[SortCode](s => {
    Try(s.as[String].map(_.asDigit)).toOption.flatMap(SortCode(_)) match {
      case Some(p) => JsSuccess(p)
      case None    => JsError(s"couldn't read SortCode out of the json: $s")
    }
  })
}
