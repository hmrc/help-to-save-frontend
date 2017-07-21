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

import cats.data.Validated.Invalid
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.syntax.CartesianBuilder
import cats.syntax.cartesian._
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo.ContactDetails

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex

case class NSIUserInfo (forename: String,
                        surname: String,
                        dateOfBirth: LocalDate,
                        nino: String,
                        contactDetails: ContactDetails,
                        registrationChannel: String = "online")

object NSIUserInfo {

  private implicit class StringOps(val s: String) {
    def removeAllSpaces: String = s.replaceAll(" ", "")
  }

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

  /**
    * Performs validation checks on the given [[UserInfo]] and converts to [[NSIUserInfo]]
    * if successful.
    */
  def apply(userInfo: UserInfo): ValidatedNel[String, NSIUserInfo] = {
    (forenameValidation(transform(userInfo.forename)) |@|
      surnameValidation(transform(userInfo.surname)) |@|
      dateValidation(userInfo.dateOfBirth) |@|
      addressLineValidation(userInfo.address.lines.map(transform)) |@|
      postcodeValidation(userInfo.address.postcode.map(p ⇒ transform(p.removeAllSpaces))) |@|
      countryCodeValidation(userInfo.address.country.map(c ⇒ transform(c.removeAllSpaces)) |@|
      ninoValidation(transform(userInfo.nino.removeAllSpaces)) |@|
      emailValidation(userInfo.email)).map(
      (forename, surname, dateOfBirth, addressLines, postcode, countryCode, nino, email) ⇒
        NSIUserInfo(forename, surname, dateOfBirth, nino,
          ContactDetails(
            addressLines.line1,
            addressLines.line2,
            addressLines.line3,
            addressLines.line4,
            addressLines.line5,
            postcode,
            countryCode,
            email))
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

  private case class Email(local: String, domain: String)

  private case class AddressLines(line1: String, line2: String, line3: Option[String], line4: Option[String], line5: Option[String])

  private val allowedNameSpecialCharacters = List('-', '&', '.', ''')

  private def transform(s: String): String = s.replaceAll("\t|\n|\r", " ").trim.replaceAll("\\s{2,}", " ")

  private def forenameValidation(name: String): ValidatedNel[String, String] = {

    val characterCountUpperBoundCheck = validatedFromBoolean(name)(_.length <= 26, s"forename was larger than 26 characters")

    (commonNameChecks(name, "forename") |@| characterCountUpperBoundCheck)
      .map { case _ ⇒ name }
  }

  private def surnameValidation(name: String): ValidatedNel[String, String] = {
    val firstCharacterNonSpecial = validatedFromBoolean(name)(!_.headOption.exists(isSpecial(_)), s"surname started with special character")

    val lastCharacterNonSpecial = validatedFromBoolean(name)(!_.lastOption.exists(isSpecial(_)), s"surname ended with special character")

    val characterCountLowerBoundCheck = validatedFromBoolean(name)(_.nonEmpty, "surname did not have at least 1 character")
    val characterCountUpperBoundCheck = validatedFromBoolean(name)(_.length <= 300, s"surname was larger than 300 characters")

    (commonNameChecks(name, "surname") |@| firstCharacterNonSpecial |@|
      lastCharacterNonSpecial |@| characterCountLowerBoundCheck |@| characterCountUpperBoundCheck)
      .map { case _ ⇒ name }
  }

  private def dateValidation(date: LocalDate): ValidatedNel[String, LocalDate] = {
    val lowerBoundCheck =
      validatedFromBoolean(date)(_.isAfter(LocalDate.of(1800, 1, 1)), s"Birth date was before 01/01/1800") // scalastyle:ignore magic.number

    val upperBoundCheck =
      validatedFromBoolean(date)(_.isBefore(LocalDate.now()), s"Birth date was in the future")

    (lowerBoundCheck |@| upperBoundCheck).map { case _ ⇒ date }
  }

  private def addressLineValidation(address: List[String]): ValidatedNel[String, AddressLines] = {
    val list = address.filter(_.nonEmpty)

    val lengthCheck = validatedFromBoolean(list)(!_.exists(_.length > 35),
      "Address contained line greater than 35 characters")

    val twoLinesCheck = list match {
      case line1 :: line2 :: line3 :: line4 :: other ⇒
        Validated.Valid(AddressLines(line1, line2, Some(line3), Some(line4), Some(other.mkString(", "))))

      case line1 :: line2 :: line3 :: line4  :: Nil ⇒
        Validated.Valid(AddressLines(line1, line2, Some(line3), Some(line4), None))

      case line1 :: line2 :: line3  :: Nil ⇒
        Validated.Valid(AddressLines(line1, line2, Some(line3), None, None))

      case line1 :: line2 :: Nil ⇒
        Validated.Valid(AddressLines(line1, line2, None, None, None))

      case _ ⇒
        Validated.Invalid(NonEmptyList.of("Could not find two lines of address"))
    }
    (lengthCheck |@| twoLinesCheck).map{ case (_,l) ⇒ l }
  }

  private def postcodeValidation(postcode: Option[String]): ValidatedNel[String, String] = postcode match {
    case None ⇒
      Invalid(NonEmptyList.of("Postcode undefined"))

    case Some(p) ⇒
      //val trimmedPostcode = p.replaceAllLiterally(" ", "")
      val lengthCheck =
        validatedFromBoolean(p)(_.length <= 10, s"Postcode was longer thn 10 characters")

      lengthCheck.map(_ ⇒ p)
  }

  // TODO: Do we want to check that the country code is in the ISO 3166 list?
  private def countryCodeValidation(countryCode: Option[String]): ValidatedNel[String, Option[String]] =
    validatedFromBoolean(countryCode)(_.forall(_.length == 2), s"Country code was not 2 characters")

  private def ninoValidation(nino: String): ValidatedNel[String, String] = {
    val lengthCheck =
      validatedFromBoolean(nino)(_.length <= 9, "NINO was longer thn 9 characters")

    val regexCheck =
      regexValidation(nino)(ninoRegex, "Invalid NINO format")

    (lengthCheck |@| regexCheck).map { case _ ⇒ nino }
  }

  private def emailValidation(email: String): ValidatedNel[String, String] = {
    val lengthCheck = validatedFromBoolean(email)(_.length <= 254, "email was longer than 254 characters")
    val atCheck = validatedFromBoolean(email)(_.contains('@'), "email did not contain '@' symbol")

    // the domain is the part of the email after the last '@' symbol - this is
    // on the assumption that the domain cannot contain an '@' symbol
    val maybeEmail = email.split("@").reverse.toList match {
      case d :: (l@(_ :: _)) ⇒ Some(Email(l.mkString("@"), d))
      case _ ⇒ None
    }

    val localCheck = validatedFromBoolean(maybeEmail)(
      _.forall(_.local.length < 65), "local part of email was longer than 64 characters")

    val domainCheck = validatedFromBoolean(maybeEmail)(
      _.forall(_.domain.length < 253), "domain part of email was longer than 252 characters")

    (lengthCheck |@| atCheck |@| localCheck |@| domainCheck).map { case _ ⇒ email }
  }

  private def commonNameChecks(name: String, nameType: String): ValidatedNel[String, String] = {
    val forbiddenSpecialCharacters = specialCharacters(name, allowedNameSpecialCharacters)

    val atLeastOneCharacterCheck = validatedFromBoolean(name)(_.exists(_.isLetter), s"$nameType did not contain any characters")

    val leadingSpaceCheck = validatedFromBoolean(name)(!_.startsWith(" "), s"$nameType started with leading space")

    val numericCheck = validatedFromBoolean(name)(!_.exists(_.isDigit), s"$nameType contained numeric characters")

    val specialCharacterCheck = validatedFromBoolean(forbiddenSpecialCharacters)(_.isEmpty,
      s"$nameType contained invalid special characters")

    val firstCharacterNonSpecial = validatedFromBoolean(name)(!_.headOption.forall(c ⇒ isSpecial(c)), s"$nameType started with special character")

    val consecutiveSpecialCharacters = validatedFromBoolean(name)(!containsNConsecutiveSpecialCharacters(_, 2),
      s"$nameType contained consecutive special characters")

    (atLeastOneCharacterCheck |@| leadingSpaceCheck |@| numericCheck |@| specialCharacterCheck |@|
      firstCharacterNonSpecial |@| consecutiveSpecialCharacters).map { case _ ⇒ name }
  }

  private def validatedFromBoolean[A](a: A)(isValid: A ⇒ Boolean, ifFalse: ⇒ String): ValidatedNel[String, A] =
    if (isValid(a)) Validated.Valid(a) else Validated.Invalid(NonEmptyList.of(ifFalse))

  private def regexValidation(s: String)(regex: Regex, ifInvalid: ⇒ String): ValidatedNel[String, String] =
    validatedFromBoolean(s)(regex.pattern.matcher(_).matches(), ifInvalid)

  /**
    * The given [[Char]] is special if the following is true:
    * - it is not a whitespace character
    * - it is not alphanumeric
    * - it is not contained in `ignore`
    */
  private def isSpecial(c: Char, ignore: List[Char] = List.empty[Char]): Boolean =
    !(c == ' ' || c.isLetterOrDigit || ignore.contains(c))

  /**
    * Return a list of distinct special characters contained in the given string. Special
    * characters found which are contained in `ignore` are not returned
    */
  private def specialCharacters(s: String, ignore: List[Char] = List.empty[Char]): List[Char] =
    s.replaceAllLiterally(" ", "").filter(isSpecial(_, ignore)).toList.distinct

  /** Does the given string contain `n` or more consecutive special characters? */
  private def containsNConsecutiveSpecialCharacters(s: String, n: Int): Boolean =
    containsNConsecutive(s, n, isSpecial(_))

  /**
    * Does the given string contains `n` consecutive characters which satisfy the given predicate?
    * `n` should be one or greater.
    */
  private def containsNConsecutive(s: String,
                                   n: Int,
                                   predicate: Char ⇒ Boolean): Boolean = {
    @tailrec
    def loop(s: List[Char],
             previous: Char,
             count: Int): Boolean = s match {
      case Nil ⇒
        false

      case head :: tail ⇒
        if (predicate(head) && predicate(previous)) {
          val newCount = count + 1
          if (newCount + 1 == n) {
            true
          } else {
            loop(tail, head, newCount)
          }
        } else {
          loop(tail, head, 0)
        }
    }

    if(n == 1) {
      s.exists(predicate)
    } else if (n > 1) {
      s.toList match {
        // only loop over strings that have length two or greater
        case head :: tail ⇒ loop(tail, head, 0)
        case _ ⇒ false
      }
    } else{
      false
    }
  }

  private[models] val ninoRegex = ("""^(([A-CEGHJ-PR-TW-Z][A-CEGHJ-NPR-TW-Z])([0-9]{2})([0-9]{2})""" +
    """([0-9]{2})([A-D]{1})|((XX)(99)(99)(99)(X)))$""").r

}

