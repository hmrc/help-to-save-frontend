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


package object models {

  implicit val contactPreferenceArb =
    Arbitrary(Gen.oneOf[ContactPreference](ContactPreference.Email, ContactPreference.SMS))


  implicit val userDetailsArb =
    Arbitrary(for{
      name ← Gen.alphaStr
      nino ← Gen.alphaNumStr
      dob ← Gen.choose(0L,100L).map(LocalDate.ofEpochDay)
      email ← Gen.alphaNumStr
      phone ← Gen.choose(0,100).map(_.toString)
      address ← Gen.listOf(Gen.alphaStr)
      contactPreference ← contactPreferenceArb.arbitrary
    } yield UserDetails(name, nino, dob, email, phone, address, contactPreference))

  def randomUserDetails(): UserDetails =
    userDetailsArb.arbitrary.sample.getOrElse(sys.error("Could not generate user details"))

}
