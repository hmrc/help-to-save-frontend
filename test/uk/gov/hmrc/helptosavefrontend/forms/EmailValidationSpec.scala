/*
 * Copyright 2018 HM Revenue & Customs
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

import cats.data.Validated.Valid
import cats.syntax.either._
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpec}
import play.api.data.Forms.{mapping, text}
import play.api.data.{Form, FormError}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.forms.EmailValidation.ErrorMessages._
import uk.gov.hmrc.helptosavefrontend.forms.EmailValidation.FormOps

// scalastyle:off magic.number
class EmailValidationSpec extends WordSpec with Matchers with GeneratorDrivenPropertyChecks with TestSupport {

  "EmailValidation" must {

      def genString(length: Int) = Gen.listOfN(length, Gen.alphaChar).map(_.mkString(""))

      def test(emailValidation: EmailValidation)(value: String)(expectedResult: Either[Set[String], Unit], log: Boolean = false): Unit = {
        val result: Either[Seq[FormError], String] = emailValidation.emailFormatter.bind("key", Map("key" → value))
        if (log) Logger.error(value + ": " + result.toString)
        result.leftMap(_.toSet) shouldBe expectedResult.bimap(_.map(s ⇒ FormError("key", s)), _ ⇒ value)
      }

      def newValidation(maxTotalLength:  Int = Int.MaxValue,
                        maxLocalLength:  Int = Int.MaxValue,
                        maxDomainLength: Int = Int.MaxValue): EmailValidation =
        new EmailValidation(Configuration(
          "email-validation.max-total-length" → maxTotalLength,
          "email-validation.max-local-length" → maxLocalLength,
          "email-validation.max-domain-length" → maxDomainLength
        ))

    "validate against blank strings" in {
      val emailValidation = newValidation()
      test(emailValidation)("")(Left(Set(blankEmailAddress, noAtSymbol, noDotSymbol, noTextAfterDotSymbol, noTextAfterAtSymbolButBeforeDot)))
    }

    "validate against the configured max total length" in {
      val emailValidation = newValidation(maxTotalLength = 5)

      forAll(genString(5), genString(5), genString(3)) { (l, d, c) ⇒
        test(emailValidation)(s"$l@$d.$c")(Left(Set(totalTooLong)))
      }
    }

    "validate against the configured max local length" in {
      val emailValidation = newValidation(maxLocalLength = 5)

      forAll(genString(6), genString(5), genString(3)) { (l, d, c) ⇒
        test(emailValidation)(s"$l@$d.$c")(Left(Set(localTooLong)))
      }
    }

    "validate against the configured max domain length" in {
      val emailValidation = newValidation(maxDomainLength = 5)

      forAll(genString(5), genString(6), genString(3)) { (l, d, c) ⇒
        test(emailValidation)(s"$l@$d.$c")(Left(Set(domainTooLong)))
      }
    }

    "validate against the presence of an @ symbol" in {
      val emailValidation = newValidation()

      // test when there is no @ symbol
      forAll(Gen.identifier, Gen.identifier, Gen.identifier) {
        case (l, d, c) ⇒
          whenever(l.nonEmpty && d.nonEmpty && c.nonEmpty) {
            test(emailValidation)(s"$l$d.$c")(Left(Set(noAtSymbol)))
          }
      }
    }

    "validate against no characters before the '@' symbol" in {
      val emailValidation = newValidation()

      forAll(Gen.identifier) { d ⇒
        whenever(d.nonEmpty) {
          test(emailValidation)(s"@$d.com")(Left(Set(localTooShort)))
        }
      }
    }

    "validate against no characters after the '@' symbol" in {
      val emailValidation = newValidation()

      forAll(Gen.identifier){ l ⇒
        whenever(l.nonEmpty){
          test(emailValidation)(s"$l@")(Left(Set(domainTooShort, noDotSymbol, noTextAfterDotSymbol, noTextAfterAtSymbolButBeforeDot)))
        }
      }
    }

    "make sure any white spaces before and after are trimmed" in {
      val emailValidation = newValidation()
      val emailWithSpaces = "  email@gmail.com  "

      val result = emailValidation.validate(emailWithSpaces)
      result shouldBe Valid("email@gmail.com")
    }

    "have an implicit class which provides methods" which {

      case class TestData(s: String)

      val key = "key"

      val form: Form[TestData] = Form(
        mapping(
          key → text
        )(TestData.apply)(TestData.unapply)
      )

        def formWithErrorMessage(message: String): Form[TestData] =
          form.copy(errors = List(FormError(key, message)))

      "informs if the form has an email which is too long" in {
        form.emailTotalLengthTooLong("text") shouldBe false
        formWithErrorMessage("error").emailTotalLengthTooLong(key) shouldBe false
        formWithErrorMessage(totalTooLong).emailTotalLengthTooLong(key) shouldBe true
      }

      "informs if the form has an email whose local part is too long" in {
        form.emailLocalLengthTooLong("text") shouldBe false
        formWithErrorMessage("error").emailLocalLengthTooLong(key) shouldBe false
        formWithErrorMessage(localTooLong).emailLocalLengthTooLong(key) shouldBe true
      }

      "informs if the form has an email whose domain part is too long" in {
        form.emailDomainLengthTooLong("text") shouldBe false
        formWithErrorMessage("error").emailDomainLengthTooLong(key) shouldBe false
        formWithErrorMessage(domainTooLong).emailDomainLengthTooLong(key) shouldBe true
      }

      "informs if the form has an email which has no @ symbol" in {
        form.emailHasNoAtSymbol("text") shouldBe false
        formWithErrorMessage("error").emailHasNoAtSymbol(key) shouldBe false
        formWithErrorMessage(noAtSymbol).emailHasNoAtSymbol(key) shouldBe true
      }

      "informs if the form has an email which is blank" in {
        form.emailIsBlank("text") shouldBe false
        formWithErrorMessage("error").emailIsBlank(key) shouldBe false
        formWithErrorMessage(blankEmailAddress).emailIsBlank(key) shouldBe true
      }

      "informs if the form has no dot symbol in the domain part" in {
        formWithErrorMessage(noDotSymbol).emailHasNoDotSymbol(key) shouldBe true
      }

      "informs if the form has no text after dot symbol in the domain part" in {
        formWithErrorMessage(noTextAfterDotSymbol).emailHasNoTextAfterDotSymbol(key) shouldBe true
      }

      "informs if the form has no text after @ and before dot symbol" in {
        formWithErrorMessage(noTextAfterAtSymbolButBeforeDot).emailHasNoTextAfterAtSymbolButBeforeDot(key) shouldBe true
      }

    }

  }

}

// scalastyle:on magic.number

