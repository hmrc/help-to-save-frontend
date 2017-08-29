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

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json._

class NSIUserInfoSpec extends WordSpec with Matchers {

  "The NSIUSerInfo" must {

    "have JSON format" which {

      "reads and writes NSIUserInfo" in {
        Json.fromJson[NSIUserInfo](Json.toJson(validNSIUserInfo)) shouldBe JsSuccess(validNSIUserInfo)
      }

      "reads and writes dates in the format 'yyyyMMdd'" in {
        val date = LocalDate.of(1234, 5, 6)

        // check the happy path
        val json1 = Json.toJson(validNSIUserInfo.copy(dateOfBirth = date))
        (json1 \ "dateOfBirth").get shouldBe JsString("12340506")

        // check the read will fail if the date is in the wrong format
        val json2 = json1.as[JsObject] ++ Json.obj("dateOfBirth" → JsString("not a date"))
        Json.fromJson[NSIUserInfo](json2).isError shouldBe true

        // check that read will fail if the date is not a string
        val json3 = json1.as[JsObject] ++ Json.obj("dateOfBirth" → JsNumber(0))
        Json.fromJson[NSIUserInfo](json3).isError shouldBe true
      }
    }

    "have an apply method" which {

      "takes in a UserInfo" which {

        "converts appropriately" in {
          NSIUserInfo(validUserInfo) shouldBe validNSIUserInfo
        }

        "removes new line, tab and carriage return in forename" in {
          val modifiedForename = "\n\t\rname\t"
          val userInfo = NSIUserInfo(validUserInfo.copy(forename = modifiedForename))
          userInfo.forename shouldBe "name"
        }

        "removes white spaces in forename" in {
          val forenameWithSpaces = " " + "forename" + " "
          val userInfo = NSIUserInfo(validUserInfo.copy(forename = forenameWithSpaces))
          userInfo.forename shouldBe "forename"
        }

        "removes spaces, tabs, new lines and carriage returns from a double barrel forename" in {
          val forenameDoubleBarrel = "   John\t\n\r   Paul\t\n\r   "
          val userInfo = NSIUserInfo(validUserInfo.copy(forename = forenameDoubleBarrel))
          userInfo.forename shouldBe "John Paul"
        }

        "removes spaces, tabs, new lines and carriage returns from a double barrel forename with a hyphen" in {
          val forenameDoubleBarrel = "   John\t\n\r-Paul\t\n\r   "
          val userInfo = NSIUserInfo(validUserInfo.copy(forename = forenameDoubleBarrel))
          userInfo.forename shouldBe "John -Paul"
        }

        "removes whitespace from surname" in {
          val userInfo = NSIUserInfo(validUserInfo.copy(surname = " surname"))
          userInfo.surname shouldBe "surname"
        }

        "removes leading and trailing whitespaces, tabs, new lines and carriage returns from double barrel surname" in {
          val modifiedSurname = "   Luis\t\n\r   Guerra\t\n\r   "
          val userInfo = NSIUserInfo(validUserInfo.copy(surname = modifiedSurname))
          userInfo.surname shouldBe "Luis Guerra"
        }

        "removes leading and trailing whitespaces, tabs, new lines and carriage returns from double barrel surname with a hyphen" in {
          val modifiedSurname = "   Luis\t\n\r-Guerra\t\n\r   "
          val userInfo = NSIUserInfo(validUserInfo.copy(surname = " " + modifiedSurname))
          userInfo.surname shouldBe "Luis -Guerra"
        }

        "removes new lines, tabs, carriage returns and trailing whitespaces from all address lines" in {
          val specialAddress =
            Address(
              List(
                "\naddress \tline1\r  ",
                " line2",
                "line3\t  ",
                "line4",
                "line5\t\n  "
              ),
              Some("BN43 XXX"),
              None
            )
          val ui: UserInfo = validUserInfo.copy(address = specialAddress)
          val userInfo = NSIUserInfo(ui)
          userInfo.contactDetails.address1 shouldBe "address line1"
          userInfo.contactDetails.address2 shouldBe "line2"
          userInfo.contactDetails.address3 shouldBe Some("line3")
          userInfo.contactDetails.address4 shouldBe Some("line4")
          userInfo.contactDetails.address5 shouldBe Some("line5")
          userInfo.contactDetails.postcode shouldBe "BN43XXX"
        }

        "removes leading and trailing whitespaces, new lines, tabs, carriage returns from all address lines" in {
          val specialAddress = Address(List(
            "   Address\t\n\r   line1\t\n\r   ",
            "   Address\t\n\r   line2\t\n\r   ",
            "   Address\t\n\r   line3\t\n\r   ",
            "   Address\t\n\r   line4\t\n\r   ",
            "   Address\t\n\r   line5\t\n\r   "), Some("BN43 XXX"),
                                       None
          )

          val ui: UserInfo = validUserInfo.copy(address = specialAddress)
          val userInfo = NSIUserInfo(ui)
          userInfo.contactDetails.address1 shouldBe "Address line1"
          userInfo.contactDetails.address2 shouldBe "Address line2"
          userInfo.contactDetails.address3 shouldBe Some("Address line3")
          userInfo.contactDetails.address4 shouldBe Some("Address line4")
          userInfo.contactDetails.address5 shouldBe Some("Address line5")
          userInfo.contactDetails.postcode shouldBe "BN43XXX"
        }

        "removes any spaces bigger than 1 character" in {
          val longName = "John Paul      \n\t\r   Harry"
          val longSurname = "  Smith    Brown  \n\r  "
          val specialAddress = Address(List(
            "   Address\t\n\r     line1\t\n\r   ",
            "   Address\t\n\r     line2\t\n\r   ",
            "   Address\t\n\rline3\t\n\r   ",
            "   Address\t\n\r   line4\t\n\r   ",
            "   Address\t\n\r             line5\t\n\r   "),
                                       Some("BN43XXX  \t\r\n"),
                                       Some("GB    \n\r\t")
          )

          val ui: UserInfo = validUserInfo.copy(forename = longName, surname = longSurname, address = specialAddress)

          val userInfo = NSIUserInfo(ui)
          userInfo.forename shouldBe "John Paul Harry"
          userInfo.surname shouldBe "Smith Brown"
          userInfo.contactDetails.address1 shouldBe "Address line1"
          userInfo.contactDetails.address2 shouldBe "Address line2"
          userInfo.contactDetails.address3 shouldBe Some("Address line3")
          userInfo.contactDetails.address4 shouldBe Some("Address line4")
          userInfo.contactDetails.address5 shouldBe Some("Address line5")
          userInfo.contactDetails.postcode shouldBe "BN43XXX"
          userInfo.contactDetails.countryCode shouldBe Some("GB")
        }

        "returns a blank string for the postcode if it is not present" in {
          val ui: UserInfo = validUserInfo.copy(address = validUserInfo.address.copy(postcode = None))
          NSIUserInfo(ui).contactDetails.postcode shouldBe ""
        }

        "returns a blank string for address lines 1 or 2 if they are missing" in {
          // check when there are no address lines
          val ui1: UserInfo = validUserInfo.copy(address =
            validUserInfo.address.copy(lines = List()))

          NSIUserInfo(ui1).contactDetails.address1 shouldBe ""
          NSIUserInfo(ui1).contactDetails.address2 shouldBe ""
          NSIUserInfo(ui1).contactDetails.address3 shouldBe None
          NSIUserInfo(ui1).contactDetails.address4 shouldBe None
          NSIUserInfo(ui1).contactDetails.address5 shouldBe None

          // check when there is only one address line
          val ui2: UserInfo = validUserInfo.copy(address =
            validUserInfo.address.copy(lines = List("line")))

          NSIUserInfo(ui2).contactDetails.address1 shouldBe "line"
          NSIUserInfo(ui2).contactDetails.address2 shouldBe ""
          NSIUserInfo(ui2).contactDetails.address3 shouldBe None
          NSIUserInfo(ui2).contactDetails.address4 shouldBe None
          NSIUserInfo(ui2).contactDetails.address5 shouldBe None
        }

        "filter out address lines which are empty" in {
          // check that lines with nothing in them get filitered out
          val willBeFilteredOut = {
            val l = List("\t", "\n", "\r", " ", "")
            l ::: l.combinations(2).flatten.toList
          }

          val ui: UserInfo = validUserInfo.copy(address =
            validUserInfo.address.copy(lines = willBeFilteredOut ::: List("line")))

          NSIUserInfo(ui).contactDetails.address1 shouldBe "line"
          NSIUserInfo(ui).contactDetails.address2 shouldBe ""
          NSIUserInfo(ui).contactDetails.address3 shouldBe None
          NSIUserInfo(ui).contactDetails.address4 shouldBe None
          NSIUserInfo(ui).contactDetails.address5 shouldBe None
        }

      }
    }

  }

}
