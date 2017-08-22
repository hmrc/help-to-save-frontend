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

case class EligibilityCheckResult(result: Either[IneligibilityReason, EligibilityReason])

sealed trait EligibilityReason {
  val legibleString: String
}

sealed trait IneligibilityReason {
  val legibleString: String
}

object EligibilityReason {

  /** In receipt of UC and income sufficient */
  case object UC extends EligibilityReason {
    val legibleString: String =
      "In receipt of UC and income sufficient"
  }

  /** Entitled to WTC and in receipt of positive WTC/CTC Tax Credit */
  case object WTC extends EligibilityReason {
    val legibleString: String =
      "Entitled to WTC and in receipt of positive WTC/CTC Tax Credit"
  }

  /** Entitled to WTC and in receipt of positive WTC/CTC Tax Credit and in receipt of UC and income sufficient */
  case object WTCWithUC extends EligibilityReason {
    val legibleString: String =
      "Entitled to WTC and in receipt of positive WTC/CTC Tax Credit and in receipt of UC and income sufficient"
  }

  def fromInt(i: Int): Option[EligibilityReason] = i match {
    case 6 ⇒ Some(UC) // scalastyle:ignore magic.number
    case 7 ⇒ Some(WTC) // scalastyle:ignore magic.number
    case 8 ⇒ Some(WTCWithUC) // scalastyle:ignore magic.number
    case _ ⇒ None
  }
}


object IneligibilityReason {

  /** An HtS account was opened previously (the HtS account may have been closed or inactive) */
  case object AccountAlreadyOpened extends IneligibilityReason {
    val legibleString: String =
      "An HtS account was opened previously (the HtS account may have been closed or inactive)"
  }

  /**
    * Not entitled to WTC and
    * (if receivingUC = true)  in receipt of UC but income is insufficient
    * (if receivingUC = false) not in receipt of UC
    **/
  case class NotEntitledToWTC(receivingUC: Boolean) extends IneligibilityReason {
    val legibleString: String = if (receivingUC) {
      "Not entitled to WTC and in receipt of UC but income is insufficient"
    } else {
      "Not entitled to WTC and not in receipt of UC"
    }
  }

  /**
    * Entitled to WTC but not in receipt of positive WTC/CTC Tax Credit (nil TC) and
    * (if receivingUC = true)  in receipt of UC but income is insufficient
    * (if receivingUC = false) not in receipt of UC
    **/
  case class EntitledToWTCButNoWTC(receivingUC: Boolean) extends IneligibilityReason {
    val legibleString: String = if (receivingUC) {
      "Entitled to WTC but not in receipt of positive WTC/CTC Tax Credit (nil TC) and in receipt of UC but income is insufficient"
    } else {
      "Entitled to WTC but not in receipt of positive WTC/CTC Tax Credit (nil TC) and not in receipt of UC"
    }
  }

  def fromInt(i: Int): Option[IneligibilityReason] = i match {
    case 1 ⇒ Some(AccountAlreadyOpened)
    case 2 ⇒ Some(NotEntitledToWTC(receivingUC = false))
    case 3 ⇒ Some(EntitledToWTCButNoWTC(receivingUC = false))
    case 4 ⇒ Some(EntitledToWTCButNoWTC(receivingUC = true)) // scalastyle:ignore magic.number
    case 5 ⇒ Some(NotEntitledToWTC(receivingUC = true)) // scalastyle:ignore magic.number
    case _ ⇒ None
  }

}

