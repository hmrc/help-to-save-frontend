package uk.gov.hmrc.helptosavefrontend.util

import java.util.Base64
import javax.crypto.spec.DESKeySpec
import javax.crypto.{Cipher, SecretKey, SecretKeyFactory}

import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.mongoEncSeed

object DataEncrypter {

  val keySpec: DESKeySpec = new DESKeySpec(mongoEncSeed.getBytes("UTF-8"))
  val keyFactory: SecretKeyFactory = SecretKeyFactory.getInstance("DES")
  val key: SecretKey = keyFactory.generateSecret(keySpec)
  val cipher: Cipher = Cipher.getInstance("DES")

  def encrypt(data: String): String = {
    cipher.init(Cipher.ENCRYPT_MODE, key)
    base64Encode(cipher.doFinal(data.getBytes("UTF-8")))
  }

  def decrypt(data: String): String = {
    cipher.init(Cipher.DECRYPT_MODE, key)
    new String(cipher.doFinal(base64Decode(data)), "UTF-8")
  }

  private def base64Encode(bytes: Array[Byte]) = Base64.getEncoder.encodeToString(bytes)

  private def base64Decode(property: String) = Base64.getDecoder.decode(property)
}
