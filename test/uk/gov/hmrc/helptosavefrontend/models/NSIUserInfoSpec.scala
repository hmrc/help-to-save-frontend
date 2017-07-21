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
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo.ContactDetails

class NSIUserInfoSpec extends WordSpec with Matchers with GeneratorDrivenPropertyChecks {

  val specialCharacters: List[Char] = (Char.MinValue to Char.MaxValue).toList.filter(!_.isLetterOrDigit)

  val address: Address = validUserInfo.address
  val postcode: String = validNSIUserInfo.contactDetails.postcode
  val forename: String = validNSIUserInfo.forename
  val surname: String = validNSIUserInfo.surname
  val email: String = validNSIUserInfo.contactDetails.email

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
          NSIUserInfo(validUserInfo.copy(forename = ".")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(forename = "-")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(forename = "&")).isInvalid shouldBe true
        }

        "contains numeric characters" in {
          NSIUserInfo(validUserInfo.copy(forename = "Tyrion2")).isInvalid shouldBe true
        }

        "contains special characters which aren't ''', '-', '.' or '&'" in {
          NSIUserInfo(validUserInfo.copy(forename = "Tyr&ion")).isValid shouldBe true
          NSIUserInfo(validUserInfo.copy(forename = "Tyr-ion")).isValid shouldBe true
          NSIUserInfo(validUserInfo.copy(forename = "Tyr.ion")).isValid shouldBe true
          NSIUserInfo(validUserInfo.copy(forename = "Tyr'ion")).isValid shouldBe true


          forAll(Gen.oneOf(specialCharacters)) { c: Char ⇒
            whenever(c != '&' && c != '-' && c != '.' && c != ''') {
              NSIUserInfo(validUserInfo.copy(forename = s"Tyr${c}ion")).isInvalid shouldBe true
            }
          }
        }

        "starts with ''', '-', '.' or '&'" in {
          NSIUserInfo(validUserInfo.copy(forename = "-Tyrion")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(forename = "&Tyrion")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(forename = ".Tyrion")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(forename = "'Tyrion")).isInvalid shouldBe true
        }

        "contains consecutive special characters" in {
          val combos = List('-', '.', '&', ''').combinations(2).map(_.mkString("")).toList
          combos.foreach(c ⇒
            NSIUserInfo(validUserInfo.copy(forename = s"Tyr${c}ion")).isInvalid shouldBe true
          )
        }

        "are longer than 26 characters" in {
          val maxLength = 26

          forAll(Gen.alphaStr.map(_.take(maxLength)).filter(_.nonEmpty)) { s ⇒
            NSIUserInfo(validUserInfo.copy(forename = s)).isValid shouldBe true
          }

          forAll(Gen.alphaStr.filter(s ⇒ s.length > maxLength)) { s ⇒
            NSIUserInfo(validUserInfo.copy(forename = s)).isInvalid shouldBe true
          }
        }
      }
    }

    "validating surnames" should {

      "mark as invalid surnames" which {

        "do not have at least one character" in {
          NSIUserInfo(validUserInfo.copy(surname = "")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = ".")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "-")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "&")).isInvalid shouldBe true
        }

        "contains numeric characters" in {
          NSIUserInfo(validUserInfo.copy(surname = "Lannister3")).isInvalid shouldBe true
        }

        "contains special characters which aren't ''', '-', '.', or '&'" in {
          NSIUserInfo(validUserInfo.copy(surname = "Lann&ister")).isValid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "Lann-ister")).isValid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "Lann.ister")).isValid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "Lann'ister")).isValid shouldBe true

          forAll(Gen.oneOf(specialCharacters)) { c: Char ⇒
            whenever(c != '&' && c != '-' && c != '.' && c!= ''') {
              NSIUserInfo(validUserInfo.copy(surname = s"Lann${c}ister")).isInvalid shouldBe true
            }
          }
        }

        "starts or ends with ''', '-', '.' or '&'" in {
          NSIUserInfo(validUserInfo.copy(surname = "-Lannister")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "&Lannister")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = ".Lannister")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "'Lannister")).isInvalid shouldBe true


          NSIUserInfo(validUserInfo.copy(surname = "Lannister-")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "Lannister&")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "Lannister.")).isInvalid shouldBe true
          NSIUserInfo(validUserInfo.copy(surname = "Lannister'")).isInvalid shouldBe true
        }

        "contains consecutive special characters" in {
          val combos = List('-', '.', '&', ''').combinations(2).map(_.mkString("")).toList
          combos.foreach(c ⇒
            NSIUserInfo(validUserInfo.copy(surname = s"Lann${c}ister")).isInvalid shouldBe true
          )
        }

        "are longer than 300 characters" in {
          val maxLength = 300
          forAll(Gen.alphaStr.map(_.take(maxLength)).filter(_.length > 1)) { s ⇒
            NSIUserInfo(validUserInfo.copy(surname = s)).isValid shouldBe true
          }

          forAll(Gen.alphaStr.map(s ⇒ s + s).filter(s ⇒ s.length > maxLength)) { s ⇒
            NSIUserInfo(validUserInfo.copy(surname = s)).isInvalid shouldBe true
          }
        }
      }
    }

    "validating date of births" should {

      "mark as invalid date of births" which {

        "are before 1st January 1800" in {
          forAll(Gen.choose(1L, 1000L)) { d ⇒ // scalastyle:ignore magic.number
            // 1st Jan 1880 is 62,091 days before Epoch
            NSIUserInfo(validUserInfo.copy(dateOfBirth = LocalDate.ofEpochDay(-62091 - d))).isInvalid shouldBe true // scalastyle:ignore magic.number

          }
        }

        "are in the future" in {
          forAll(Gen.choose(1L, 1000L)) { d ⇒ // scalastyle:ignore magic.number
            NSIUserInfo(validUserInfo.copy(dateOfBirth = LocalDate.now().plusDays(d))).isInvalid shouldBe true
          }
        }

      }
    }

    "validating addresses" should {

      "mark as invalid addresses" which {
        val emptyAddress = Address(List.empty[String], Some(postcode), None)


        "do not have at least two lines" in {

          NSIUserInfo(validUserInfo.copy(address = emptyAddress)).isInvalid shouldBe true

          // test one line addresses are invalid
          NSIUserInfo(validUserInfo.copy(
            address = emptyAddress.copy(lines = List("1 the Street")))).isInvalid shouldBe true

          NSIUserInfo(validUserInfo.copy(
            address = emptyAddress.copy(lines = List("1 the Street", "")))).isInvalid shouldBe true

          NSIUserInfo(validUserInfo.copy(
            address = emptyAddress.copy(lines = List("", "1 the Street")))).isInvalid shouldBe true

          // test two line addresses are valid
          val info = NSIUserInfo(validUserInfo.copy(
            address = emptyAddress.copy(
              List("1 the Street", "The Place")))
          ).getOrElse(sys.error("Expected valid NSIUserInformation"))

          info.contactDetails.address1 shouldBe "1 the Street"
          info.contactDetails.address2 shouldBe "The Place"
        }

        "contain lines which are longer than 35 characters" in {
          forAll { s: String ⇒
            whenever(s.length > 35) {
              NSIUserInfo(validUserInfo.copy(
                address = emptyAddress.copy(
                  lines = List(s,s)
                ))).isInvalid shouldBe true
            }
          }
        }
      }
    }

    "validating postcodes" should {

      "mark as invalid postcodes" which {

        "are longer than 10 characters when trimmed" in {
          forAll { p: String ⇒
            whenever(p.replaceAllLiterally(" ", "").length > 10) {
              NSIUserInfo(validUserInfo.copy(address = address.copy(postcode = Some(p)))).isInvalid shouldBe true
            }
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
          forAll { (c1: Char, c2: Char) ⇒
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

        "which do not contain an '@' sign" in {
          NSIUserInfo(validUserInfo.copy(email = email.filterNot(_ == '@'))).isInvalid shouldBe true
        }

        "has a local part greater than 64 characters" in {
          forAll { s: String ⇒
            whenever(s.length <= 64) {
              NSIUserInfo(validUserInfo.copy(email = s + "@test.com")).isValid shouldBe true
            }
          }

          forAll { s: String ⇒
            whenever(s.length > 64) {
              NSIUserInfo(validUserInfo.copy(email = s + "@test.com")).isInvalid shouldBe true
            }
          }
        }


        "has a domain part greater than 252 characters" in {
          val maxLength = 252

          forAll { s: String ⇒
            whenever(s.length <= maxLength) {
              NSIUserInfo(validUserInfo.copy(email = "a@" + s)).isValid shouldBe true
            }
          }

          forAll { s: String ⇒
            // create a bigger string here so that the property check below
            // is more likely to succeed
            val t = List.fill(10)(s).mkString("") // scalastyle:ignore magic.number
            whenever(t.filter(_ != '@').length > maxLength) {
              NSIUserInfo(validUserInfo.copy(email = "a@" + t.filter(_ != '@'))).isInvalid shouldBe true
            }
          }
        }
      }
    }


    "validating ninos" should {

      "mark as invalid nino's" which {

        "are invalid" in {
          val generator = new Generator()
          forAll(Gen.function0(generator.nextNino.nino)) { nino ⇒
            NSIUserInfo(validUserInfo.copy(nino = nino())).isValid shouldBe true
          }

          forAll { s: String ⇒
            whenever(!Nino.isValid(s)) {
              NSIUserInfo(validUserInfo.copy(nino = s)).isInvalid shouldBe true
            }
          }
        }
      }

    }

    "transform" should {

      "remove new line, tab and carriage return in forename" in {
        val modifiedForename = "\n\t\rname\t"
        val userInfo = NSIUserInfo(validUserInfo.copy(forename = modifiedForename))
        (userInfo.map{_.forename} == Valid("name"))
      }

      "remove white spaces in forename" in {
        val forenameWithSpaces = " " + "forename" + " "
        val userInfo = NSIUserInfo(validUserInfo.copy(forename = forenameWithSpaces))
        (userInfo.map{_.forename} == Valid("forename"))
      }

      "remove spaces, tabs, new lines and carriage returns from a double barrel forename" in {
        val forenameDoubleBarrel = "   John\t\n\r   Paul\t\n\r   "
        val userInfo = NSIUserInfo(validUserInfo.copy(forename = forenameDoubleBarrel))
        (userInfo.map{_.forename} == Valid("John Paul"))
      }

      "remove spaces, tabs, new lines and carriage returns from a double barrel forename with a hyphen" in {
        val forenameDoubleBarrel = "   John\t\n\r-Paul\t\n\r   "
        val userInfo = NSIUserInfo(validUserInfo.copy(forename = forenameDoubleBarrel))
        (userInfo.map{_.forename} == Valid("John-Paul"))
      }

      "remove whitespace from surname" in {
        val userInfo = NSIUserInfo(validUserInfo.copy(surname = " " + surname))
        (userInfo.map{_.surname} == Valid(surname)) shouldBe true
      }

      "remove leading and trailing whitespaces, tabs, new lines and carriage returns from double barrel surname" in {
        val modifiedSurname = "   Luis\t\n\r   Guerra\t\n\r   "
        val userInfo = NSIUserInfo(validUserInfo.copy(surname = modifiedSurname))
        (userInfo.map{_.surname} == Valid("Luis Guerra"))
      }

      "remove leading and trailing whitespaces, tabs, new lines and carriage returns from double barrel surname with a hyphen" in {
        val modifiedSurname = "   Luis\t\n\r-Guerra\t\n\r   "
        val userInfo = NSIUserInfo(validUserInfo.copy(surname = " " + modifiedSurname))
        (userInfo.map{_.surname} == Valid("Luis-Guerra"))
      }

      "remove new lines, tabs, carriage returns and trailing whitespaces from all address lines" in {
        val specialAddress = Address(List("\naddress \tline1\r  ", " line2", "line3\t  ", "line4", "line5\t\n  "),
          Some("BN43 XXX"), None)
        val ui: UserInfo = validUserInfo.copy(address = specialAddress)
        val userInfo = NSIUserInfo(ui)
        (userInfo.map{_.contactDetails.address1}) shouldBe Valid("address line1")
        (userInfo.map{_.contactDetails.address2}) shouldBe Valid("line2")
        (userInfo.map{_.contactDetails.address3}) shouldBe Valid(Some("line3"))
        (userInfo.map{_.contactDetails.address4}) shouldBe Valid(Some("line4"))
        (userInfo.map{_.contactDetails.address5}) shouldBe Valid(Some("line5"))
      }

      "remove leading and trailing whitespaces, new lines, tabs, carriage returns from all address lines" in {
        val specialAddress = Address(List("   Address\t\n\r   line1\t\n\r   ", "   Address\t\n\r   line2\t\n\r   ", "   Address\t\n\r   line3\t\n\r   ",
          "   Address\t\n\r   line4\t\n\r   ", "   Address\t\n\r   line5\t\n\r   "), Some("BN43 XXX"), None)
        val ui: UserInfo = validUserInfo.copy(address = specialAddress)
        val userInfo = NSIUserInfo(ui)
        (userInfo.map{_.contactDetails.address1}) shouldBe Valid("Address line1")
        (userInfo.map{_.contactDetails.address2}) shouldBe Valid("Address line2")
        (userInfo.map{_.contactDetails.address3}) shouldBe Valid(Some("Address line3"))
        (userInfo.map{_.contactDetails.address4}) shouldBe Valid(Some("Address line4"))
        (userInfo.map{_.contactDetails.address5}) shouldBe Valid(Some("Address line5"))
      }

      "remove any spaces bigger than 1 character" in {
        val longName = "John Paul      \n\t\r   Harry"
        val longSurname = "  Smith    Brown  \n\r  "
        val specialAddress = Address(List("   Address\t\n\r     line1\t\n\r   ", "   Address\t\n\r     line2\t\n\r   ", "   Address\t\n\rline3\t\n\r   ",
          "   Address\t\n\r   line4\t\n\r   ", "   Address\t\n\r             line5\t\n\r   "), Some("BN43XXX  \t\r\n"), Some("GB    \n\r\t"))
        val ui: UserInfo = validUserInfo.copy(forename = longName, surname = longSurname, address = specialAddress)
        val userInfo = NSIUserInfo(ui)
        (userInfo.map{_.forename}) shouldBe Valid("John Paul Harry")
        (userInfo.map{_.surname}) shouldBe Valid("Smith Brown")
        (userInfo.map{_.contactDetails.address1}) shouldBe Valid("Address line1")
        (userInfo.map{_.contactDetails.address2}) shouldBe Valid("Address line2")
        (userInfo.map{_.contactDetails.address3}) shouldBe Valid(Some("Address line3"))
        (userInfo.map{_.contactDetails.address4}) shouldBe Valid(Some("Address line4"))
        (userInfo.map{_.contactDetails.address5}) shouldBe Valid(Some("Address line5"))
        (userInfo.map{_.contactDetails.postcode}) shouldBe Valid("BN43XXX")
        (userInfo.map{_.contactDetails.countryCode}) shouldBe Valid(Some("GB"))
      }
    }
  }
}