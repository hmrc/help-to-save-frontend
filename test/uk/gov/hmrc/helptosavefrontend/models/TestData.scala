/*
 * Copyright 2023 HM Revenue & Customs
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

import org.scalacheck.Gen
import org.scalacheck.Gen.alphaStr
import uk.gov.hmrc.helptosavefrontend.models.HTSSession.EligibleWithUserInfo
import uk.gov.hmrc.helptosavefrontend.models.eligibility.EligibilityCheckResultType.{Eligible, Ineligible}
import uk.gov.hmrc.helptosavefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIPayload.ContactDetails
import uk.gov.hmrc.helptosavefrontend.models.userinfo.{Address, NSIPayload, UserInfo}
import uk.gov.hmrc.helptosavefrontend.testutil._
import uk.gov.hmrc.smartstub.{AdvGen, Enumerable}

import java.time.LocalDate
import scala.language.implicitConversions

object TestData {

  object Eligibility {

    implicit val eligibilityCheckResponseGen: Gen[EligibilityCheckResponse] = {
      val eligibleResponseGen: Gen[EligibilityCheckResult] =
        for {
          result     <- Gen.alphaStr
          reasonCode <- Gen.choose(6, 8)
          reason     <- Gen.alphaStr
        } yield EligibilityCheckResult(result, 1, reason, reasonCode)

      for {
        result    <- eligibleResponseGen
        threshold <- Gen.option(Gen.posNum[Double])
      } yield EligibilityCheckResponse(result, threshold)
    }

    implicit val eligibilityGen: Gen[Eligible] = for {
      reasonCode <- Gen.oneOf(6, 7, 8)
    } yield Eligible(EligibilityCheckResponse(EligibilityCheckResult("", 1, "", reasonCode), Some(134.45)))

    implicit val ineligibilityGen: Gen[Ineligible] = for {
      reasonCode <- Gen.oneOf(3, 4, 5, 9)
    } yield Ineligible(EligibilityCheckResponse(EligibilityCheckResult("", 2, "", reasonCode), Some(134.45)))

    implicit val ineligibilityReason5: Ineligible =
      Ineligible(EligibilityCheckResponse(EligibilityCheckResult("", 2, "", 5), Some(134.45)))

    implicit val ineligibilityReason5WithNoThreshold: Ineligible =
      Ineligible(EligibilityCheckResponse(EligibilityCheckResult("", 2, "", 5), None))

    implicit def ineligibilityReason4or9Gen(threshold: Option[Double]): Gen[Ineligible] =
      for {
        reasonCode <- Gen.oneOf(4, 9)
      } yield Ineligible(EligibilityCheckResponse(EligibilityCheckResult("", 2, "", reasonCode), threshold))

    def randomEligibilityResponse(): EligibilityCheckResponse = sample(eligibilityCheckResponseGen)

    def randomEligibility(): Eligible = sample(eligibilityGen)

    def randomIneligibility(): Ineligible = sample(ineligibilityGen)

    def notEntitledToWTCAndUCInsufficient(): Ineligible = ineligibilityReason5

    def notEntitledToWTCAndUCInsufficientWithNoThreshold(): Ineligible = ineligibilityReason5WithNoThreshold

    def ineligibilityReason4or9(): Ineligible = sample(ineligibilityReason4or9Gen(Some(123.45)))

    def ineligibilityReason4or9WithNoThreshold(): Ineligible = sample(ineligibilityReason4or9Gen(None))

    def randomEligibleWithUserInfo(userInfo: UserInfo): EligibleWithUserInfo =
      EligibleWithUserInfo(randomEligibility(), userInfo)

    def eligibleSpecificReasonCodeWithUserInfo(userInfo: UserInfo, reasonCode: Int): EligibleWithUserInfo =
      EligibleWithUserInfo(
        Eligible(EligibilityCheckResponse(EligibilityCheckResult("", 1, "", reasonCode), Some(134.45))),
        userInfo
      )
  }

  object UserData {
    implicit val addressGen: Gen[Address] =
      for {
        lines    <- Gen.ukAddress
        postcode <- Gen.option(Gen.postcode)
        country  <- Gen.option(alphaStr)
      } yield Address(lines, postcode, country)

    implicit val userInfoGen: Gen[UserInfo] =
      for {
        forename    <- Gen.forename()
        surname     <- Gen.surname
        nino        <- Enumerable.instances.ninoEnum.gen
        dateOfBirth <- Gen.date
        email       <- Gen.option(alphaStr)
        address     <- addressGen
      } yield UserInfo(forename, surname, nino, dateOfBirth, email, address)

    def randomUserInfo(): UserInfo = sample(userInfoGen)

    /**
      * Valid user details which will pass NSI validation checks
      */
    val (validUserInfo, nsiValidContactDetails, validNSIPayload) = {
      val (forename, surname) = "Tyrion" -> "Lannister"
      val dateOfBirth = LocalDate.ofEpochDay(0L)
      val addressLine1 = "Casterly Rock"
      val addressLine2 = "The Westerlands"
      val addressLine3 = "Westeros"
      val postcode = "BA148FY"
      val country = "GB"
      val address = Address(List(addressLine1, addressLine2, addressLine3), Some(postcode), Some(country))
      val nino = "WM123456C"
      val email = "tyrion_lannister@gmail.com"

      val userInfo = UserInfo(forename, surname, nino, dateOfBirth, Some(email), address)
      val nsiValidContactDetails =
        ContactDetails(addressLine1, addressLine2, Some(addressLine3), None, None, postcode, Some(country), email)
      val nsiPayload =
        NSIPayload(
          forename,
          surname,
          dateOfBirth,
          nino,
          ContactDetails(addressLine1, addressLine2, Some(addressLine3), None, None, postcode, Some(country), email),
          "online",
          None,
          "V2.0",
          "systemId"
        )

      (userInfo, nsiValidContactDetails, nsiPayload)
    }
  }

}
