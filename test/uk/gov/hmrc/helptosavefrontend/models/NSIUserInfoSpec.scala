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

import java.time.LocalDate

import cats.data.Validated.Valid
import org.scalacheck.Gen
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import uk.gov.hmrc.domain.{Generator, Nino}

class NSIUserInfoSpec extends WordSpec with Matchers with GeneratorDrivenPropertyChecks {

  val specialCharacters: List[Char] = (Char.MinValue to Char.MaxValue).toList.filter(!_.isLetterOrDigit)

  val address = validUserInfo.address
  val postcode = validNSIUserInfo.postcode
  val forename = validNSIUserInfo.forename
  val surname = validNSIUserInfo.surname
  val email = validNSIUserInfo.emailAddress

  "The NSIUSerInfo" when {

    "validating validUserInfo" must {

      "mark as valid valid user information" in {
        NSIUserInfo(validUserInfo) shouldBe Valid(validNSIUserInfo)
      }

    }

    "validating forenames" should {

      "mark as invalid forenames" which {

        "are empty" in {
          NSIUserInfo(validUserInfo.copy(forename = "")).isInvalid shouldBe true
        }

        "start with whitespace" in {
          NSIUserInfo(validUserInfo.copy(forename = " " + forename)).isInvalid shouldBe true
        }

        "contains numeric characters" in {
          NSIUserInfo(validUserInfo.copy(forename = "Tyrion2")).isInvalid shouldBe true
        }

        "contains special characters which aren't '-' or '&'" in {
          NSIUserInfo(validUserInfo.copy(forename = "Tyr&ion")).isValid shouldBe true
          NSIUserInfo(validUserInfo.copy(forename = "Tyr-ion")).isValid shouldBe true

          forAll(Gen.oneOf(specialCharacters)){ c: Char ⇒
            whenever(c != '&' && c != '-'){
              NSIUserInfo(validUserInfo.copy(forename = s"Tyr${c}ion")).isInvalid shouldBe true
            }
          }
        }

        "starts or ends with '-' or '&'" in {
          NSIUserInfo(validUserInfo.copy(forename = "-Tyrion")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(forename = "&Tyrion")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(forename = "Tyrion-")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(forename = "Tyrion&")).isInvalid shouldBe true
        }

        "contains consecutive special characters" in {
          NSIUserInfo(validUserInfo.copy(forename = "Tyr--ion")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(forename = "Tyr-&ion")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(forename = "Tyr-&ion")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(forename = "Tyr&&ion")).isInvalid shouldBe true
        }

        "are longer than 26 characters" in {
          forAll(Gen.alphaStr.map(_.take(26)).filter(_.nonEmpty)){ s ⇒
            NSIUserInfo(validUserInfo.copy(forename = s)).isValid shouldBe true
          }

          forAll(Gen.alphaStr.filter(s ⇒ s.length > 26)){ s ⇒
            NSIUserInfo(validUserInfo.copy(forename = s)).isInvalid shouldBe true
          }
        }
      }
    }

    "validating surnames" should {

      "mark as invalid surnames" which {

        "do not have at least two characters" in {
          forAll{ c: Char ⇒
            NSIUserInfo(validUserInfo.copy(surname = c.toString)).isInvalid shouldBe true
          }
        }

        "start with whitespace" in {
          NSIUserInfo(validUserInfo.copy(surname = " " + surname)).isInvalid shouldBe true
        }

        "contains numeric characters" in {
          NSIUserInfo(validUserInfo.copy(surname = "Lannister3")).isInvalid shouldBe true
        }

        "contains special characters which aren't '-' or '&'" in {
          NSIUserInfo(validUserInfo.copy(surname = "Lann&ister")).isValid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "Lann-ister")).isValid shouldBe true

          forAll(Gen.oneOf(specialCharacters)){ c: Char ⇒
            whenever(c != '&' && c != '-'){
              NSIUserInfo(validUserInfo.copy(surname = s"Lann${c}ister")).isInvalid shouldBe true
            }
          }
        }

        "starts or ends with '-' or '&'" in {
          NSIUserInfo(validUserInfo.copy(surname = "-Lannister")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "&Lannister")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "Lannister-")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "Lannister&")).isInvalid shouldBe true
        }

        "contains consecutive special characters" in {
          NSIUserInfo(validUserInfo.copy(surname = "Lann--ister")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "Lann-&ister")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "Lann&-ister")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "Lann&&ister")).isInvalid shouldBe true
        }

        "not not contain at least two consecutive alphabetic characters" in {
          NSIUserInfo(validUserInfo.copy(surname = "L&L")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "L-L")).isInvalid shouldBe true
        }

        "are longer than 300 characters" in {
          forAll(Gen.alphaStr.map(_.take(300)).filter(_.length > 1)){ s ⇒
            NSIUserInfo(validUserInfo.copy(surname = s)).isValid shouldBe true
          }

          forAll(Gen.alphaStr.map(s ⇒ s + s).filter(s ⇒ s.length > 300)){ s ⇒
            NSIUserInfo(validUserInfo.copy(surname = s)).isInvalid shouldBe true
          }
        }
      }
    }

    "validating date of births" should {

      "mark as invalid date of births" which {

        "are before 1st January 1800" in {
          forAll(Gen.choose(1L, 1000L)){ d ⇒
            // 1st Jan 1880 is 62,091 days before Epoch
            NSIUserInfo(validUserInfo.copy(dateOfBirth = LocalDate.ofEpochDay(-62091 - d))).isInvalid shouldBe true
          }
        }

        "are in the future" in {
          forAll(Gen.choose(1L, 1000L)){ d ⇒
            NSIUserInfo(validUserInfo.copy(dateOfBirth = LocalDate.now().plusDays(d))).isInvalid shouldBe true
          }
        }

      }
    }

    "validating addresses" should {

      "mark as invalid addresses" which {

        "do not have at least two lines" in {
          val emptyAddress = Address(None, None, None, None, None, Some(postcode), None)

          NSIUserInfo(validUserInfo.copy(address = emptyAddress)).isInvalid shouldBe true

          // test one line addresses are invalid
          NSIUserInfo(validUserInfo.copy(
            address = emptyAddress.copy(line1 = Some("1 the Street")))).isInvalid shouldBe true

          NSIUserInfo(validUserInfo.copy(
            address = emptyAddress.copy(line2 = Some("1 the Street")))).isInvalid shouldBe true

          NSIUserInfo(validUserInfo.copy(
            address = emptyAddress.copy(line3 = Some("1 the Street")))).isInvalid shouldBe true

          NSIUserInfo(validUserInfo.copy(
            address = emptyAddress.copy(line4 = Some("1 the Street")))).isInvalid shouldBe true

          NSIUserInfo(validUserInfo.copy(
            address = emptyAddress.copy(line5 = Some("1 the Street")))).isInvalid shouldBe true

          // test two line addresses are valid
          def test(validUserInfo: UserInfo): Unit = {
            val info = NSIUserInfo(validUserInfo.copy(
              address = emptyAddress.copy(
                line1 = Some("1 the Street"),
                line2 = Some("The Place")))
            ).getOrElse(sys.error("Expected valid NSIUserInformation"))
            info.address1 shouldBe "1 the Street"
            info.address2 shouldBe "The Place"
            info.address3 shouldBe None
            info.address4 shouldBe None
            info.address5 shouldBe None
          }

          test(validUserInfo.copy(
            address = emptyAddress.copy(
              line1 = Some("1 the Street"),
              line2 = Some("The Place"))))

          test(validUserInfo.copy(
            address = emptyAddress.copy(
              line1 = Some("1 the Street"),
              line3 = Some("The Place"))))

          test(validUserInfo.copy(
            address = emptyAddress.copy(
              line1 = Some("1 the Street"),
              line4 = Some("The Place"))))

          test(validUserInfo.copy(
            address = emptyAddress.copy(
              line1 = Some("1 the Street"),
              line5 = Some("The Place"))))

          test(validUserInfo.copy(
            address = emptyAddress.copy(
              line2 = Some("1 the Street"),
              line3 = Some("The Place"))))

          test(validUserInfo.copy(
            address = emptyAddress.copy(
              line2 = Some("1 the Street"),
              line4 = Some("The Place"))))

          test(validUserInfo.copy(
            address = emptyAddress.copy(
              line2 = Some("1 the Street"),
              line5 = Some("The Place"))))

          test(validUserInfo.copy(
            address = emptyAddress.copy(
              line3 = Some("1 the Street"),
              line4 = Some("The Place"))))

          test(validUserInfo.copy(
            address = emptyAddress.copy(
              line3 = Some("1 the Street"),
              line5 = Some("The Place"))))

          test(validUserInfo.copy(
            address = emptyAddress.copy(
              line4 = Some("1 the Street"),
              line5 = Some("The Place"))))

        }
      }
    }

    "validating postcodes" should {

      "mark as invalid postcodes" which {

        "do not match the correct format" in {
          // examples taken from the NS&I Interface Control Document (ICD)
          val postcodes = List(
            "GIR0AA",
            "A00AA",
            "A000AA",
            "AA00AA",
            "AA000AA",
            "AA0A0AA",
            "A0A0AA",
            "A1ZZ",
            "AA1ZZ",
            "AAA1ZZ",
            "AAAA1ZZ",
            "BFPO0",
            "BFPO00",
            "BFPO000",
            "BFPO0000"
          )

          postcodes.foreach{ p ⇒
            NSIUserInfo(validUserInfo.copy(address = address.copy(postcode = Some(p)))).isValid shouldBe true
          }
        }
      }
    }

    "validating country codes" should {

      "mark as invalid country codes" which {

        "are not two characters long when the country code is defined" in {
          // None should be allowed
          NSIUserInfo(validUserInfo.copy(address = address.copy(country = None))).isValid shouldBe true

          // two letters should be allowed
          forAll{ (c1: Char, c2: Char) ⇒
            val code = c1.toString + c2.toString
            NSIUserInfo(validUserInfo.copy(address = address.copy(country = Some(code)))).isValid shouldBe true
          }

          forAll { (s: String) ⇒
            whenever(s.length != 2) {
              NSIUserInfo(validUserInfo.copy(address = address.copy(country = Some(s)))).isInvalid shouldBe true
            }
          }
        }
      }
    }

    "validating emails" should {

      "mark as invalid emails" which {

        "which contain whitespace" in {
          NSIUserInfo(validUserInfo.copy(email = email + " ")).isInvalid shouldBe true
        }

        "which do not contain an '@' sign" in {
          NSIUserInfo(validUserInfo.copy(email = email.filterNot(_ == '@'))).isInvalid shouldBe true
        }

        "which have a '.' sign immediately before the '@' sign" in {
          NSIUserInfo(validUserInfo.copy(email = "tyrion_lannister.@gmail.com")).isInvalid shouldBe true
        }

        "which do not finish with at least 2 characters after the last '.'" in {
          NSIUserInfo(validUserInfo.copy(email = "tyrion_lannister@gmail.c")).isInvalid shouldBe true
        }

      }
    }


    "validating ninos" should {

      "mark as invalid nino's" which {

        "are invalid" in {
          val generator = new Generator()
          forAll(Gen.function0(generator.nextNino.nino)){ nino ⇒
            NSIUserInfo(validUserInfo.copy(NINO = nino())).isValid shouldBe true
          }

          forAll{ s: String ⇒
            whenever(!Nino.isValid(s)){
              NSIUserInfo(validUserInfo.copy(NINO = s)).isInvalid shouldBe true
            }
          }
        }
      }

    }
  }

}
