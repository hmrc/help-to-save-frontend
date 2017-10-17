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

import cats.Eq
import cats.instances.boolean._
import cats.instances.string._
import cats.syntax.eq._

sealed trait EligibilityCheckResult

object EligibilityCheckResult {

  case class Eligible(reason: EligibilityReason) extends EligibilityCheckResult

  case class Ineligible(reason: IneligibilityReason) extends EligibilityCheckResult

  case class AlreadyHasAccount(description: String) extends EligibilityCheckResult

  implicit class Ops(val result: EligibilityCheckResult) extends AnyVal {
    def fold[A](ifEligible:          EligibilityReason ⇒ A,
                ifIneligible:        IneligibilityReason ⇒ A,
                ifAlreadyHasAccount: AlreadyHasAccount ⇒ A): A = result match {
      case Eligible(reason)     ⇒ ifEligible(reason)
      case Ineligible(reason)   ⇒ ifIneligible(reason)
      case a: AlreadyHasAccount ⇒ ifAlreadyHasAccount(a)
    }

  }
}

sealed trait EligibilityReason {
  val description: String
}

sealed trait IneligibilityReason {
  val description: String
}

object EligibilityReason {

  /** In receipt of UC and income sufficient */
  case class UC(description: String) extends EligibilityReason

  /** Entitled to WTC and in receipt of positive WTC/CTC Tax Credit */
  case class WTC(description: String) extends EligibilityReason

  /** Entitled to WTC and in receipt of positive WTC/CTC Tax Credit and in receipt of UC and income sufficient */
  case class WTCWithUC(description: String) extends EligibilityReason

  def fromInt(i: Int, s: String): Option[EligibilityReason] = i match {
    case 6 ⇒ Some(UC(s)) // scalastyle:ignore magic.number
    case 7 ⇒ Some(WTC(s)) // scalastyle:ignore magic.number
    case 8 ⇒ Some(WTCWithUC(s)) // scalastyle:ignore magic.number
    case _ ⇒ None
  }
}

object IneligibilityReason {

  implicit def eqInstance: Eq[IneligibilityReason] = new Eq[IneligibilityReason] {
    override def eqv(x: IneligibilityReason, y: IneligibilityReason): Boolean = (x, y) match {
      case (NotEntitledToWTC(r1, s1), NotEntitledToWTC(r2, s2)) if r1 === r2 && s1 === s2 ⇒ true
      case (EntitledToWTCButNoWTC(r1, s1), EntitledToWTCButNoWTC(r2, s2)) if r1 === r2 && s1 === s2 ⇒ true
      case _ ⇒ false
    }
  }

  /**
   * Not entitled to WTC and
   * (if receivingUC = true)  in receipt of UC but income is insufficient
   * (if receivingUC = false) not in receipt of UC
   */
  case class NotEntitledToWTC(receivingUC: Boolean, description: String) extends IneligibilityReason

  /**
   * Entitled to WTC but not in receipt of positive WTC/CTC Tax Credit (nil TC) and
   * (if receivingUC = true)  in receipt of UC but income is insufficient
   * (if receivingUC = false) not in receipt of UC
   */
  case class EntitledToWTCButNoWTC(receivingUC: Boolean, description: String) extends IneligibilityReason

  def fromInt(i: Int, s: String): Option[IneligibilityReason] = i match {
    case 2 ⇒ Some(NotEntitledToWTC(receivingUC = false, s))
    case 3 ⇒ Some(EntitledToWTCButNoWTC(receivingUC = false, s))
    case 4 ⇒ Some(EntitledToWTCButNoWTC(receivingUC = true, s)) // scalastyle:ignore magic.number
    case 5 ⇒ Some(NotEntitledToWTC(receivingUC = true, s)) // scalastyle:ignore magic.number
    case _ ⇒ None
  }

}

