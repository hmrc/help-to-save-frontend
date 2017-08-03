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
import scala.util.{Failure, Success, Try}

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

    val wsClientConfig = config.copy(
      ssl = config.ssl.copy(
        keyManagerConfig = config.ssl.keyManagerConfig.copy(
          keyStoreConfigs = keyStores
        ),
        trustManagerConfig = config.ssl.trustManagerConfig.copy(
          trustStoreConfigs = customTrustStore(configuration)
        )
      )
    )

    wsClientConfig
  }

  private def customTrustStore(config: Configuration): Seq[TrustStoreConfig] = {
    val cacerts = config.getString("custom-trustManager.path")
    val cacertsPass = config.getString("custom-trustManager.password")
    val trustData = config.getString("custom-trustManager.data")

    (cacerts, cacertsPass, trustData) match {
      case (Some(cacertsPath), Some(cacertsPassword), Some(trustStoreData)) if !trustStoreData.equals("") ⇒
        createTempFileForData(trustStoreData) match {
          case Success(trustFile) ⇒

            logger.info(s"loading default cacerts from: $cacerts")
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
            val decryptedPass = new String(Base64.getDecoder.decode(cacertsPassword))
            keyStore.load(new FileInputStream(cacertsPath), decryptedPass.toCharArray)

            val certs = generateCertificates(trustFile)

            certs.foreach { cert ⇒
              val alias = cert.asInstanceOf[X509Certificate].getSubjectX500Principal.getName
              keyStore.setCertificateEntry(alias, cert)
            }

            keyStore.store(new FileOutputStream(cacertsPath), decryptedPass.toCharArray)

            List(TrustStoreConfig(filePath = Some(cacertsPath), data = None))

          case Failure(error) ⇒
            logger.error(s"Error storing trust data in temp file", error)
            sys.error(s"Error storing trust data in temp file: ${error.getMessage}")
        }

      case (_, _, _) ⇒
        logger.error(s"no config found for truststore - continuing... ")
        List.empty //TODO: ideally we should do sys.error but tests failing
    }
  }

  private def generateCertificates(file: File): Seq[Certificate] = {

    val dis = new DataInputStream(new FileInputStream(file))
    val bytes = new Array[Byte](dis.available)
    dis.readFully(bytes)
    val bais = new ByteArrayInputStream(bytes)

    val cf = CertificateFactory.getInstance("X.509")
    val certs = cf.generateCertificates(bais)
    certs.toList
  }

  def createTempFileForData(data: String) = Try {
    val file = File.createTempFile(getClass.getSimpleName, ".tmp")
    file.deleteOnExit()
    val os = new FileOutputStream(file)
    os.write(Base64.getDecoder.decode(data.trim))
    os.flush()
    os.close()
    file
  }

  private def createKeyStoreConfig(ks: KeyStoreConfig, data: String): KeyStoreConfig = {
    logger.info("Creating key store config with the encrypted data provided")

    createTempFileForData(data) match {
      case Success(keyStoreFile) ⇒
        logger.info(s"Successfully wrote keystore to file: ${keyStoreFile.getAbsolutePath}")

        val decryptedPass = ks.password
          .map(pass ⇒ Base64.getDecoder.decode(pass))
          .map(bytes ⇒ new String(bytes))

        ks.copy(data = None, filePath = Some(keyStoreFile.getAbsolutePath), password = decryptedPass)

      case Failure(error) ⇒
        logger.info(s"Error in keystore configuration: ${error.getMessage}", error)
        sys.error(s"Error in keystore configuration: ${error.getMessage}")
    }
  }
}

class CustomWSConfigParserModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[WSConfigParser].to[CustomWSConfigParser]
    )
  }

}