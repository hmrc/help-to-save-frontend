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

package uk.gov.hmrc.helptosavefrontend

import java.time.LocalDate

import org.scalacheck.{Arbitrary, Gen}
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo.ContactDetails
import uk.gov.hmrc.helptosavefrontend.testutil._


package object models {

  implicit val addressArb =
    Arbitrary(for {
      line1 ← Gen.alphaNumStr
      line2 ← Gen.alphaNumStr
      line3 ← Gen.alphaNumStr
      line4 ← Gen.alphaNumStr
      line5 ← Gen.alphaNumStr
      postcode ← Gen.alphaNumStr
      country ← Gen.alphaStr
    } yield Address(List(line1, line2, line3, line4, line5),
      Some(postcode), Some(country)))

  implicit val userInfoArb =
    Arbitrary(for {
      name ← Gen.alphaStr
      surname ← Gen.alphaStr
      nino ← Gen.alphaNumStr
      dob ← Gen.choose(0L, 100L).map(LocalDate.ofEpochDay)
      email ← Gen.alphaNumStr
      address ← addressArb.arbitrary
    } yield UserInfo(name, surname, nino, dob, email, address))

  def randomUserInfo(): UserInfo = sample(userInfoArb)



  /**
    * Valid user details which will pass NSI validation checks
    */
  val (validUserInfo, validNSIUserInfo) = {
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

    val userInfo = UserInfo(forename, surname, nino, dateOfBirth, email, address)
    val nsiUserInfo =
      NSIUserInfo(forename, surname, dateOfBirth, nino,
        ContactDetails(addressLine1, addressLine2, Some(addressLine3), None, None, postcode, Some(country), email)
      )

    userInfo → nsiUserInfo
  }

}
