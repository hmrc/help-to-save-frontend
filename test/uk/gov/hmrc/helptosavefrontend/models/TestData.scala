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

import java.time.LocalDate

import org.scalacheck.Gen
import uk.gov.hmrc.helptosavefrontend.models.HTSSession.EligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResult.{Eligible, Ineligible}
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResponse
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIUserInfo.ContactDetails
import uk.gov.hmrc.helptosavefrontend.models.userinfo.{Address, NSIUserInfo, UserInfo}
import uk.gov.hmrc.helptosavefrontend.testutil._
import uk.gov.hmrc.smartstub.AutoGen.{GenProvider, instance}
import uk.gov.hmrc.smartstub.{AutoGen, _}

object TestData {

  object Eligibility {

    implicit val eligibilityCheckResponseGen: Gen[EligibilityCheckResponse] = AutoGen[EligibilityCheckResponse]

    implicit val eligibilityGen: Gen[Eligible] = for {
      reasonCode ← Gen.oneOf(6, 7, 8)
    } yield Eligible(EligibilityCheckResponse("", 1, "", reasonCode))

    implicit val ineligibilityGen: Gen[Ineligible] = for {
      reasonCode ← Gen.oneOf(2, 3, 4, 5, 9)
    } yield Ineligible(EligibilityCheckResponse("", 2, "", reasonCode))

    def randomEligibilityResponse() = sample(eligibilityCheckResponseGen)

    def randomEligibility() = sample(eligibilityGen)

    def randomIneligibility() = sample(ineligibilityGen)

    def randomEligibleWithUserInfo(userInfo: UserInfo) = EligibleWithUserInfo(randomEligibility(), userInfo)
  }

  object UserData {
    implicit def providerLocalDate(s: String): GenProvider[LocalDate] = instance({
      s.toLowerCase match {
        case "dateofbirth" | "dob" | "birthdate" ⇒ Gen.date(LocalDate.of(1900, 1, 1), LocalDate.now())
        case _                                   ⇒ Gen.date
      }
    })

    implicit val userInfoGen: Gen[UserInfo] = AutoGen[UserInfo]

    def randomUserInfo(): UserInfo = sample(userInfoGen)

    /**
     * Valid user details which will pass NSI validation checks
     */
    val (validUserInfo, nsiValidContactDetails, validNSIUserInfo) = {
      val (forename, surname) = "Tyrion" → "Lannister"
      val dateOfBirth = LocalDate.ofEpochDay(0L)
      val addressLine1 = "Casterly Rock"
      val addressLine2 = "The Westerlands"
      val addressLine3 = "Westeros"
      val postcode = "BA148FY"
      val country = "GB"
      val address = Address(List(addressLine1, addressLine2, addressLine3),
                            Some(postcode), Some(country))
      val nino = "WM123456C"
      val email = "tyrion_lannister@gmail.com"

      val userInfo = UserInfo(forename, surname, nino, dateOfBirth, Some(email), address)
      val nsiValidContactDetails = ContactDetails(addressLine1, addressLine2, Some(addressLine3), None, None, postcode, Some(country), email)
      val nsiUserInfo =
        NSIUserInfo(forename, surname, dateOfBirth, nino,
                    ContactDetails(addressLine1, addressLine2, Some(addressLine3), None, None, postcode, Some(country), email)
        )

      (userInfo, nsiValidContactDetails, nsiUserInfo)
    }
  }

}
