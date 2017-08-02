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
import java.time.format.DateTimeFormatter

import cats.Show
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo.ContactDetails

import scala.util.{Failure, Success, Try}

case class NSIUserInfo (forename: String,
                        surname: String,
                        dateOfBirth: LocalDate,
                        nino: String,
                        contactDetails: ContactDetails,
                        registrationChannel: String = "online")

object NSIUserInfo {

  case class ContactDetails(address1: String,
                            address2: String,
                            address3: Option[String],
                            address4: Option[String],
                            address5: Option[String],
                            postcode: String,
                            countryCode: Option[String],
                            email: String,
                            phoneNumber: Option[String] = None,
                            communicationPreference: String = "02")

  implicit val localDateShow: Show[LocalDate] = Show.show(date ⇒ date.format(DateTimeFormatter.ofPattern("dd/MM/YYYY")))

  private implicit class StringOps(val s: String) {
    def removeAllSpaces: String = s.replaceAll(" ", "")

    def cleanupSpecialCharacters: String = s.replaceAll("\t|\n|\r", " ").trim.replaceAll("\\s{2,}", " ")

  }

  /**
    * Performs validation checks on the given [[UserInfo]] and converts to [[NSIUserInfo]]
    * if successful.
    */
  def apply(userInfo: UserInfo): NSIUserInfo = {
    def extractContactDetails(userInfo: UserInfo): ContactDetails = {
      val (line1, line2, line3, line4, line5) =
        userInfo.address.lines.map(_.cleanupSpecialCharacters).filter(_.nonEmpty) match {
          case Nil                                                ⇒ ("", "", None, None, None)
          case line1 :: Nil                                       ⇒ (line1, "", None, None, None)
          case line1 :: line2 :: Nil                              ⇒ (line1, line2, None, None, None)
          case line1 :: line2 :: line3 :: Nil                     ⇒ (line1, line2, Some(line3), None, None)
          case line1 :: line2 :: line3 :: line4 :: Nil            ⇒ (line1, line2, Some(line3), Some(line4), None)
          case line1 :: line2 :: line3 :: line4 :: line5 :: Nil   ⇒ (line1, line2, Some(line3), Some(line4), Some(s"$line5"))
          case line1 :: line2 :: line3 :: line4 :: line5 :: other ⇒ (line1, line2, Some(line3), Some(line4), Some(s"$line5,${other.mkString(",")}"))
        }

      ContactDetails(
        line1, line2, line3, line4, line5,
        userInfo.address.postcode.getOrElse("").cleanupSpecialCharacters.removeAllSpaces,
        userInfo.address.country.map(_.cleanupSpecialCharacters.removeAllSpaces),
        userInfo.email
      )
    }

    NSIUserInfo(
      userInfo.forename.cleanupSpecialCharacters,
      userInfo.surname.cleanupSpecialCharacters,
      userInfo.dateOfBirth,
      userInfo.nino.cleanupSpecialCharacters.removeAllSpaces,
      extractContactDetails(userInfo)
    )
  }

  implicit val dateFormat: Format[LocalDate] = new Format[LocalDate] {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    override def writes(o: LocalDate): JsValue = JsString(o.format(formatter))

    override def reads(json: JsValue): JsResult[LocalDate] = json match {
      case JsString(s) ⇒ Try(LocalDate.parse(s, formatter)) match {
        case Success(date) ⇒ JsSuccess(date)
        case Failure(error) ⇒ JsError(s"Could not parse date as yyyyMMdd: ${error.getMessage}")
      }

      case other ⇒ JsError(s"Expected string but got $other")
    }
  }

  implicit val contactDetailsFormat: Format[ContactDetails] = Json.format[ContactDetails]

  implicit val nsiUserInfoFormat: Format[NSIUserInfo] = Json.format[NSIUserInfo]

}
