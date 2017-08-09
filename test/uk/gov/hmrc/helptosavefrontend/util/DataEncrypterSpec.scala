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
