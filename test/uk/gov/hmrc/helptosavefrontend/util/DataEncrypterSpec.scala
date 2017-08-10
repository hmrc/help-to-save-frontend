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

package uk.gov.hmrc.helptosavefrontend.util

import uk.gov.hmrc.helptosavefrontend.TestSupport

class DataEncrypterSpec extends TestSupport {

  "The DataEncrypter" must {

    "correctly encrypt and decrypt the data given" in {

      val original = "user+mailbox/department=shipping@example.com"

      val encoded = DataEncrypter.encrypt(original)

      val decoded = DataEncrypter.decrypt(encoded)

      decoded should be(original)
    }

    "correctly encrypt and decrypt the data when there are special characters" in {

      val original = "Dörte@Sören!#$%&'*+-/=?^_`उपयोगकर्ता@उदाहरण.कॉम.{|}~@example.com"

      val encoded = DataEncrypter.encrypt(original)

      val decoded = DataEncrypter.decrypt(encoded)

      decoded should be(original)
    }
  }
}
