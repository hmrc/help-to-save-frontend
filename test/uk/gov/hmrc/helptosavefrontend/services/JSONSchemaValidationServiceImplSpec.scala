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

package uk.gov.hmrc.helptosavefrontend.services

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.models.validNSIUserInfo

class JSONSchemaValidationServiceImplSpec extends TestSupport {

  val fakeConfiguration = fakeApplication.configuration
  val service = new JSONSchemaValidationServiceImpl(fakeConfiguration)

  val validUserInfoJSON: JsValue = Json.toJson(validNSIUserInfo)

  implicit class JsValueOps(val jsValue: JsValue) {

    def replace[A <: JsValue](field: String, newValue: A): JsValue =
      jsValue.as[JsObject] ++ Json.obj(field → newValue)

    def replaceInner[A <: JsValue](field: String, innerField: String, newValue: A): JsValue = {
      val inner = (jsValue \ field).getOrElse(sys.error(s"Could not find field $field"))
      val innerReplaced = inner.replace(innerField, newValue)
      jsValue.replace(field, innerReplaced)
    }

    def remove(field: String): JsValue =
      jsValue.as[JsObject] - field

    def removeInner(field: String, innerField: String): JsValue = {
      val inner = (jsValue \ field).getOrElse(sys.error(s"Could not find field $field"))
      val innerReplaced = inner.remove(innerField)
      jsValue.replace(field, innerReplaced)
    }

  }

  object Fields {
    val forename = "forename"
    val surname = "surname"
    val dob = "dateOfBirth"
    val contactDetails = "contactDetails"
    val registrationChannel = "registrationChannel"
    val countryCode = "countryCode"
    val address1 = "address1"
    val address2 = "address2"
    val address3 = "address3"
    val address4 = "address4"
    val address5 = "address5"
    val postcode = "postcode"
    val communicationPreference = "communicationPreference"
    val phoneNumber = "phoneNumber"
    val email = "email"
    val nino = "nino"
  }

  "The JSONSchemaValidationServiceImpl" must {

      def testError(userInfo: JsValue): Unit =
        service.validate(userInfo).isLeft shouldBe true

    val dateTimeFormmater = DateTimeFormatter.BASIC_ISO_DATE

    "If the outgoing-json validation feature detects no errors return a right" in {
      service.validate(validUserInfoJSON) shouldBe Right(validUserInfoJSON)
    }

    "If the outgoing-json validation feature detects a birth date prior to 1800 it returns a left" in {
      testError(
        validUserInfoJSON.replace(
          Fields.dob,
          JsString(LocalDate.of(1799, 5, 5).format(dateTimeFormmater))
        ))
    }

    "If the outgoing-json validation feature detects a birth date just after to 1800 it returns a right" in {
      service.validate(
        validUserInfoJSON.replace(
          Fields.dob,
          JsString(LocalDate.of(1800, 1, 1).format(dateTimeFormmater))
        )).isRight shouldBe true
    }

    "If the outgoing-json futureDate function detects a birth date in the future it returns a left " in {
      testError(
        validUserInfoJSON.replace(
          Fields.dob,
          JsString(LocalDate.now().plusDays(1).format(dateTimeFormmater))
        ))
    }

    "when given a NSIUserInfo that the json validation schema reports that the forename is the wrong type, return a message" in {
      testError(validUserInfoJSON.replace(Fields.forename, JsNumber(0)))
    }

    "when given a NSIUserInfo that the json validation schema reports that the forename is too short, return a message" in {
      testError(validUserInfoJSON.replace(Fields.forename, JsString("")))
    }

    "when given a NSIUserInfo that the json validation schema reports that the forename is too long, return a message" in {
      testError(validUserInfoJSON.replace(Fields.forename, JsString("A" * 27)))
    }

    "when given a NSIUserInfo that the json validation schema reports that the forename is missing" in {
      testError(validUserInfoJSON.remove(Fields.forename))
    }

    "when given a NSIUserInfo that the json validation schema reports that the surname is the wrong type, return a message" in {
      testError(validUserInfoJSON.replace(Fields.surname, JsNumber(0)))
    }

    "when given a NSIUserInfo that the json validation schema reports that the surname is too short, return a message" in {
      testError(validUserInfoJSON.replace(Fields.surname, JsString("")))

    }

    "when given a NSIUserInfo that the json validation schema reports that the surname is too long, return a message" in {
      testError(validUserInfoJSON.replace(Fields.forename, JsString("A" * 301)))
    }

    "when given a NSIUserInfo that the json validation schema reports that the surname is missing" in {
      testError(validUserInfoJSON.remove(Fields.surname))
    }

    "when given a NSIUserInfo that the json validation schema reports that the date of birth is the wrong type, return a message" in {
      testError(validUserInfoJSON.replace(Fields.dob, JsBoolean(false)))
    }

    "when given a NSIUserInfo that the json validation schema reports that the dateOfBirth is too short, return a message" in {
      testError(validUserInfoJSON.replace(Fields.dob, JsString("1800505")))
    }

    "when given a NSIUserInfo that the json validation schema reports that the dateOfBirth is too long, return a message" in {
      testError(validUserInfoJSON.replace(Fields.dob, JsString("180000525")))
    }

    "when given a NSIUserInfo that the json validation schema reports that the dateOfBirth does not meet the regex, return a message" in {
      testError(validUserInfoJSON.replace(Fields.dob, JsString("18oo0525")))
    }

    "when given a NSIUserInfo that the json validation schema reports that the dateOfBirth is missing, return a message" in {
      testError(validUserInfoJSON.remove(Fields.dob))
    }

    "when given a NSIUserInfo that the json validation schema reports that the country code is the wrong type, return a message" in {
      testError(validUserInfoJSON.replaceInner(Fields.contactDetails, Fields.countryCode, JsNumber(1)))
    }

    "when given a NSIUserInfo that the json validation schema reports that the country code is too short, return a message" in {
      testError(validUserInfoJSON.replaceInner(Fields.contactDetails, Fields.countryCode, JsString("G")))
    }

    "when given a NSIUserInfo that the json validation schema reports that the country code is too long, return a message" in {
      testError(validUserInfoJSON.replaceInner(Fields.contactDetails, Fields.countryCode, JsString("GRG")))
    }

    "when given a NSIUserInfo that the json validation schema reports that the country code does not meet the regex, return a message" in {
      testError(validUserInfoJSON.replaceInner(Fields.contactDetails, Fields.countryCode, JsString("--")))
    }

      def testAddressLines(number: Int, field: String): Unit = {
        s"when given a NSIUserInfo that the json validation schema reports that the address$number field is the wrong type, return a message" in {
          testError(validUserInfoJSON.replaceInner(Fields.contactDetails, field, JsNumber(1)))
        }

        s"when given a NSIUserInfo that the json validation schema reports that the address$number field is too long" in {
          testError(validUserInfoJSON.replaceInner(Fields.contactDetails, field, JsString("A" * 36)))
        }

        if (number == 1 || number == 2) {
          s"when given a NSIUserInfo that the json validation schema reports that the address$number field is missing, return a message" in {
            testError(validUserInfoJSON.removeInner(Fields.contactDetails, field))
          }
        }
      }

    testAddressLines(1, Fields.address1)
    testAddressLines(2, Fields.address2)
    testAddressLines(3, Fields.address3)
    testAddressLines(4, Fields.address4)
    testAddressLines(5, Fields.address5)

    "when given a NSIUserInfo that the json validation schema reports that the postcode field is the wrong type, return a message" in {
      testError(validUserInfoJSON.replaceInner(Fields.contactDetails, Fields.postcode, JsNumber(1)))
    }

    "when given a NSIUserInfo that the json validation schema reports that the postcode field is too long" in {
      testError(validUserInfoJSON.replaceInner(Fields.contactDetails, Fields.postcode, JsString("P" * 11)))
    }

    "when given a NSIUserInfo that the json validation schema reports that the postcode is missing, return a message" in {
      testError(validUserInfoJSON.removeInner(Fields.contactDetails, Fields.postcode))
    }

    "when given a NSIUserInfo that the json validation schema reports that the communicationPreference field is the wrong type, return a message" in {
      testError(validUserInfoJSON.replaceInner(Fields.contactDetails, Fields.communicationPreference, JsNumber(1)))
    }

    "when given a NSIUserInfo that the json validation schema reports that the communicationPreference field is too short" in {
      testError(validUserInfoJSON.replaceInner(Fields.contactDetails, Fields.communicationPreference, JsString("")))
    }

    "when given a NSIUserInfo that the json validation schema reports that the communicationPreference field is too long" in {
      testError(validUserInfoJSON.replaceInner(Fields.contactDetails, Fields.communicationPreference, JsString("AAA")))
    }

    "when given a NSIUserInfo that the json validation schema reports that the communicationPreference field does not meet regex" in {
      testError(validUserInfoJSON.replaceInner(Fields.contactDetails, Fields.communicationPreference, JsString("01")))
    }

    "when given a NSIUserInfo that the json validation schema reports that the communicationPreference field is missing, return a message" in {
      testError(validUserInfoJSON.removeInner(Fields.contactDetails, Fields.communicationPreference))
    }

    "when given a NSIUserInfo that the json validation schema reports that the phone number field is the wrong type, return a message" in {
      testError(validUserInfoJSON.replaceInner(Fields.contactDetails, Fields.phoneNumber, JsNumber(0)))
    }

    "when given a NSIUserInfo that the json validation schema reports that the phone number is too long" in {
      testError(validUserInfoJSON.replaceInner(Fields.contactDetails, Fields.phoneNumber, JsString("A" * 16)))
    }

    "when given a NSIUserInfo that the json validation schema reports that the email address is too long" in {
      testError(validUserInfoJSON.replaceInner(Fields.contactDetails, Fields.email, JsString("A" * 63 + "@" + "A" * 251)))
    }

    "when given a NSIUserInfo that the json validation schema reports that the registration channel is too long" in {
      testError(validUserInfoJSON.replace(Fields.registrationChannel, JsString("A" * 11)))
    }

    "when given a NSIUserInfo that the json validation schema reports that the registration channel does not meet regex, return a message" in {
      testError(validUserInfoJSON.replace(Fields.registrationChannel, JsString("offline")))
    }

    "when given a NSIUserInfo that the json validation schema reports that the registration channel is missing, return a message" in {
      testError(validUserInfoJSON.remove(Fields.registrationChannel))
    }

    "when given a NSIUserInfo that the json validation schema reports that the nino is too short" in {
      testError(validUserInfoJSON.replace(Fields.nino, JsString("WM23456C")))
    }

    "when given a NSIUserInfo that the json validation schema reports that the nino is too long" in {
      testError(validUserInfoJSON.replace(Fields.nino, JsString("WM1234567C")))
    }

    "when given a NSIUserInfo that the json validation schema reports that the nino does not meet the validation regex" in {
      testError(validUserInfoJSON.replace(Fields.nino, JsString("WMAA3456C")))
    }

    "when given a NSIUserInfo that the json validation schema reports that the nino is missing, return a message" in {
      testError(validUserInfoJSON.remove(Fields.nino))
    }
  }

}
