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

package uk.gov.hmrc.helptosavefrontend.config

import java.io._
import java.security.KeyStore
import java.security.cert.{Certificate, CertificateFactory, X509Certificate}
import java.util.Base64
import javax.inject.{Inject, Singleton}

import play.api.inject.{Binding, Module}
import play.api.libs.ws.ssl.{KeyStoreConfig, TrustStoreConfig}
import play.api.libs.ws.{WSClientConfig, WSConfigParser}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.helptosavefrontend.util.Logging

import scala.collection.JavaConversions._

@Singleton
class CustomWSConfigParser @Inject()(configuration: Configuration, env: Environment) extends WSConfigParser(configuration, env) with Logging {

  logger.info("Starting CustomWSConfigParser")

  override def parse(): WSClientConfig = {

    logger.info("Parsing WSClientConfig")

    val internalParser = new WSConfigParser(configuration, env)
    val config = internalParser.parse()

    val keyStores = config.ssl.keyManagerConfig.keyStoreConfigs.filter(_.data.forall(_.nonEmpty)).map { ks ⇒
      (ks.storeType.toUpperCase, ks.filePath, ks.data) match {
        case (_, None, Some(data)) ⇒
          createKeyStoreConfig(ks, data)

        case other ⇒
          logger.info(s"Adding ${other._1} type keystore")
          ks
      }
    }

    val trustStores = config.ssl.trustManagerConfig.trustStoreConfigs.filter(_.data.forall(_.nonEmpty)).map { ts ⇒
      (ts.filePath, ts.data) match {
        case (None, Some(data)) ⇒
          createTrustStoreConfig(ts, data)

        case _ ⇒
          logger.info(s"Adding ${ts.storeType} type truststore from ${ts.filePath}")
          ts
      }
    }

    val wsClientConfig = config.copy(
      ssl = config.ssl.copy(
        keyManagerConfig = config.ssl.keyManagerConfig.copy(
          keyStoreConfigs = keyStores
        ),
        trustManagerConfig = config.ssl.trustManagerConfig.copy(
          trustStoreConfigs = trustStores
        )
      )
    )

    wsClientConfig
  }

  private def createTrustStoreConfig(ts: TrustStoreConfig, data: String): TrustStoreConfig = {

    val (filePath, fileBytes) = createTempFileForData(data)

    val keyStore = initKeystore()

    generateCertificates(fileBytes).foreach { cert ⇒
      val alias = cert.asInstanceOf[X509Certificate].getSubjectX500Principal.getName
      keyStore.setCertificateEntry(alias, cert)
    }

    val stream = new FileOutputStream(filePath)

    try {
      keyStore.store(stream, "".toCharArray)
      logger.info(s"Successfully wrote truststore data to file: $filePath")
      ts.copy(filePath = Some(filePath), data = None)
    } finally {
      stream.close()

    }
  }

  private def initKeystore(): KeyStore = {
    val keystore = KeyStore.getInstance(KeyStore.getDefaultType)
    keystore.load(null, null)
    keystore
  }

  private def generateCertificates(file: Array[Byte]): Seq[Certificate] = {
    val stream = new ByteArrayInputStream(file)
    try {
      CertificateFactory.getInstance("X.509")
        .generateCertificates(stream)
        .toList
    } finally {
      stream.close()
    }
  }

  /**
    * @return absolute file path with the bytes written to the file
    */
  def createTempFileForData(data: String): (String, Array[Byte]) = {
    val file = File.createTempFile(getClass.getSimpleName, ".tmp")
    file.deleteOnExit()
    val os = new FileOutputStream(file)
    try {
      val bytes = Base64.getDecoder.decode(data.trim)
      os.write(bytes)
      os.flush()
      file.getAbsolutePath → bytes
    } finally {
      os.close()
    }
  }

  private def createKeyStoreConfig(ks: KeyStoreConfig, data: String): KeyStoreConfig = {
    logger.info("Creating key store config")
    val (ksFilePath, _) = createTempFileForData(data)
    logger.info(s"Successfully wrote keystore data to file: $ksFilePath")

    val decryptedPass = ks.password
      .map(password ⇒ Base64.getDecoder.decode(password))
      .map(bytes ⇒ new String(bytes))

    ks.copy(data = None, filePath = Some(ksFilePath), password = decryptedPass)
  }
}

class CustomWSConfigParserModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[WSConfigParser].to[CustomWSConfigParser]
    )
  }

}