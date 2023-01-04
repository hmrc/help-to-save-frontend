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

package uk.gov.hmrc.helptosavefrontend.forms

import cats.syntax.either._
import play.api.Configuration
import play.api.data.{Form, FormError}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.controllers.ControllerSpecWithGuiceApp
import uk.gov.hmrc.helptosavefrontend.forms.BankDetailsValidation.ErrorMessages
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class BankDetailsValidationSpec extends ControllerSpecWithGuiceApp {

  val bankValidationConfig = Configuration(
    "bank-details-validation.sort-code.length" → 6,
    "bank-details-validation.account-number.length" → 8,
    "bank-details-validation.roll-number.min-length" → 4,
    "bank-details-validation.roll-number.max-length" → 18,
    "bank-details-validation.account-name.min-length" → 3,
    "bank-details-validation.account-name.max-length" → 4
  )

  lazy val validation = new BankDetailsValidation(
    new FrontendAppConfig(
      new ServicesConfig(bankValidationConfig.withFallback(fakeApplication.configuration))
    )
  )

  type Key = String

  def test[A, B](
    f: (String, Map[String, String]) ⇒ Either[Seq[FormError], A],
    insert: (Key, Option[B]) ⇒ Map[String, String]
  )(value: Option[B])(expectedResult: Either[Set[String], A]): Unit = {
    val result: Either[Seq[FormError], A] = f("key", insert("key", value))
    result.leftMap(_.toSet) shouldBe expectedResult.leftMap(_.map(s ⇒ FormError("key", s)))
  }

  def insertString(k: Key, v: Option[String]): Map[String, String] =
    v.fold(Map.empty[String, String])(v ⇒ Map(k → v))

  "BankDetailsValidation" when afterWord("validating") {

    "sort codes" must {

      val sortCode = SortCode(1, 2, 3, 4, 5, 6)

      def testSortCode(value: Option[String])(expectedResult: Either[Set[String], SortCode]): Unit =
        test[SortCode, String](validation.sortCodeFormatter.bind, insertString)(value)(expectedResult)

      "allow inputs with the configured numbered digits" when {

        "separated by nothing" in {
          testSortCode(Some("123456"))(Right(sortCode))
        }

        "separated by spaces" in {
          testSortCode(Some("12 3 45 6"))(Right(sortCode))
        }

        "separated by hyphens" in {
          testSortCode(Some("12-3-45-6"))(Right(sortCode))
        }

        "separated by dashes" in {
          testSortCode(Some("12–3–45–6"))(Right(sortCode))
        }

        "separated by long dashes" in {
          testSortCode(Some("12—3—45—6"))(Right(sortCode))
        }

        "separated by other dashes" in {
          testSortCode(Some("12−3−45−6"))(Right(sortCode))
        }

        "containing with trailing and leading spaces" in {
          testSortCode(Some("   123456 "))(Right(sortCode))
        }

        "containing control characters" in {
          testSortCode(Some("12\t34\r56\n"))(Right(sortCode))
        }

      }

      "not allow inputs" which {

        "are null" in {
          testSortCode(None)(Left(Set(ErrorMessages.sortCodeEmpty)))
        }

        "are the empty" in {
          testSortCode(Some(""))(Left(Set(ErrorMessages.sortCodeEmpty)))
        }

        "contain non-digit characters" in {
          testSortCode(Some("12345C"))(Left(Set(ErrorMessages.sortCodeIncorrectFormat)))
        }

        "have a length smaller than the configured length" in {
          testSortCode(Some("12345"))(Left(Set(ErrorMessages.sortCodeIncorrectFormat)))
        }

        "have a length greater than the configured length" in {
          testSortCode(Some("1234567"))(Left(Set(ErrorMessages.sortCodeIncorrectFormat)))
        }

        "have numbers separated by non-supported characters" in {
          List(',', '.', '&', '*', '/', '_').foreach { c ⇒
            withClue(s"For char $c: ") {
              testSortCode(Some(s"12${c}34${c}56"))(Left(Set(ErrorMessages.sortCodeIncorrectFormat)))
            }
          }

        }

      }

    }

    "account numbers" must {

      def testAccountNumber(value: Option[String])(expectedResult: Either[Set[String], String]): Unit =
        test(validation.accountNumberFormatter.bind, insertString)(value)(expectedResult)

      "allow inputs" which {
        "contains the configured number of digits" in {
          testAccountNumber(Some("12345678"))(Right("12345678"))
        }

        "contains trailing and leading spaces" in {
          testAccountNumber(Some("   12345678  "))(Right("12345678"))
        }

        "contains control characters" in {
          testAccountNumber(Some("12\t34\r56\n78"))(Right("12345678"))
        }

      }

      "not allow inputs" which {

        "are null" in {
          testAccountNumber(None)(Left(Set(ErrorMessages.accountNumberEmpty)))
        }

        "are empty" in {
          testAccountNumber(Some(""))(Left(Set(ErrorMessages.accountNumberEmpty)))
        }

        "contain non-digit characters" in {
          testAccountNumber(Some("1234567D"))(Left(Set(ErrorMessages.accountNumberIncorrectFormat)))
        }

        "have a length smaller than the configured length" in {
          testAccountNumber(Some("1"))(Left(Set(ErrorMessages.accountNumberIncorrectFormat)))

        }

        "have a length greater than the configured length" in {
          testAccountNumber(Some("123456789"))(Left(Set(ErrorMessages.accountNumberIncorrectFormat)))
        }

      }

    }

    "roll numbers" must {

      def testRollNumber(value: Option[String])(expectedResult: Either[Set[String], Option[String]]): Unit =
        test[Option[String], Option[String]](validation.rollNumberFormatter.bind, {
          case (k, o) ⇒ insertString(k, o.flatten)
        })(Some(value))(expectedResult)

      "allow inputs" which {

        "have a length within the configured limits" in {
          testRollNumber(Some("ab12"))(Right(Some("ab12")))
          testRollNumber(Some("length18stringonly"))(Right(Some("length18stringonly")))
        }

        "which contain only dots, hyphens and forward slashes" in {
          testRollNumber(Some("ab1.cd3-ef/123"))(Right(Some("ab1.cd3-ef/123")))
        }

        "is null" in {
          testRollNumber(None)(Right(None))
        }

        "are strings of zero length" in {
          testRollNumber(Some(""))(Right(None))
        }

      }

      "not allow inputs" when {

        "containing spaces in them" in {
          testRollNumber(Some(" a b "))(Left(Set(ErrorMessages.rollNumberIncorrectFormat)))
        }

        "containing control characters" in {
          testRollNumber(Some("ab1\n\r\t"))(Left(Set(ErrorMessages.rollNumberIncorrectFormat)))
        }

        "the length is less than the configured length" in {
          testRollNumber(Some("ab"))(Left(Set(ErrorMessages.rollNumberTooShort)))
        }

        "the length is greater than the configured length" in {
          testRollNumber(Some("abcdefghij123456789"))(Left(Set(ErrorMessages.rollNumberTooLong)))
        }

        "containing characters not allowed as per regex" in {
          testRollNumber(Some("a_b$c%&^@!"))(Left(Set(ErrorMessages.rollNumberIncorrectFormat)))
        }
      }

    }

    "account names" must {

      def testAccountName(value: Option[String])(expectedResult: Either[Set[String], String]): Unit =
        test(validation.accountNameFormatter.bind, insertString)(value)(expectedResult)

      "allow inputs" which {

        "have a length within the configured limits" in {
          testAccountName(Some("ab1"))(Right("ab1"))
          testAccountName(Some("ab 1"))(Right("ab 1"))
          testAccountName(Some("ab12"))(Right("ab12"))
        }

        "contains trailing and leading spaces" in {
          testAccountName(Some("  ab1   "))(Right("ab1"))
        }

        "contains control characters" in {
          testAccountName(Some("ab1\n\r\t"))(Right("ab1"))
        }

      }

      "not allow inputs" which {

        "are null" in {
          testAccountName(None)(Left(Set(ErrorMessages.accountNameEmpty)))
        }

        "are empty" in {
          testAccountName(Some(""))(Left(Set(ErrorMessages.accountNameEmpty)))
        }

        "have length less than the configured length" in {
          testAccountName(Some("ab"))(Left(Set(ErrorMessages.accountNameTooShort)))
        }

        "have length greater than the configured length" in {
          testAccountName(Some("ab123"))(Left(Set(ErrorMessages.accountNameTooLong)))
        }

      }

    }

    "BankDetailsValidation" must {

      "have method to inform of form errors" when {

        import TestForm._
        import uk.gov.hmrc.helptosavefrontend.forms.BankDetailsValidation.FormOps

        def test(formHasError: Form[TestData] ⇒ String ⇒ Boolean, message: String): Unit = {
          formHasError(testForm)(TestForm.key) shouldBe false
          formHasError(testFormWithErrorMessage("error"))(TestForm.key) shouldBe false
          formHasError(testFormWithErrorMessage(message))(TestForm.key) shouldBe true
        }

        "the sort code is empty" in {
          test(_.sortCodeEmpty, ErrorMessages.sortCodeEmpty)
        }

        "the sort code has an incorrect format" in {
          test(_.sortCodeIncorrectFormat, ErrorMessages.sortCodeIncorrectFormat)
        }

        "the sort code did not pass backend validation" in {
          test(_.sortCodeBackendInvalid, ErrorMessages.sortCodeBackendInvalid)
        }

        "the account number is empty" in {
          test(_.accountNumberEmpty, ErrorMessages.accountNumberEmpty)
        }

        "the account number has an incorrect format" in {
          test(_.accountNumberIncorrectFormat, ErrorMessages.accountNumberIncorrectFormat)
        }

        "the account number did not pass backend validation" in {
          test(_.accountNumberBackendInvalid, ErrorMessages.accountNumberBackendInvalid)
        }

        "the roll number is too short" in {
          test(_.rollNumberTooShort, ErrorMessages.rollNumberTooShort)
        }

        "the roll number is too long" in {
          test(_.rollNumberTooLong, ErrorMessages.rollNumberTooLong)
        }

        "the roll number has incorrect format" in {
          test(_.rollNumberIncorrectFormat, ErrorMessages.rollNumberIncorrectFormat)
        }

        "the account name is empty" in {
          test(_.accountNameEmpty, ErrorMessages.accountNameEmpty)
        }

        "the account name is too short" in {
          test(_.accountNameTooShort, ErrorMessages.accountNameTooShort)
        }

        "the account name is too long" in {
          test(_.accountNameTooLong, ErrorMessages.accountNameTooLong)
        }

      }

    }

  }

}
