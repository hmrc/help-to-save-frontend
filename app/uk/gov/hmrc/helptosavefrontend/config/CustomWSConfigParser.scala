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
        // it's a PEM, so we don't need to do anything
        case ("PEM", _, _) ⇒
          logger.info("Adding PEM keystore certificate")
          ks

        // it is not a PEM and data has been provided but no file path given, therefore assume data is base64 encoded file
        case (_, None, Some(data)) ⇒
          createKeyStoreConfig(ks, data)

        // just because ...
        case other ⇒
          logger.info(s"Adding ${other._1} type keystore")
          ks
      }
    }

    val updatedKeyManagerConfig = config.ssl.keyManagerConfig.copy(keyStoreConfigs = keyStores)

    val wsClientConfig = config.copy(ssl = config.ssl.copy(keyManagerConfig = updatedKeyManagerConfig))

    updateTruststore(configuration)

    wsClientConfig
  }

  private def updateTruststore(config: Configuration) = {
    Try {
      val cacertsPath = config.getString("truststore.cacerts.path").get
      logger.info(s"cacerts path $cacertsPath")
      val cacertsPass = config.getString("truststore.cacerts.password").get
      val decryptedPass = new String(Base64.getDecoder.decode(cacertsPass))
      val trustData = config.getString("truststore.data").get

      val result = for {
        dataBytes ← Try(Base64.getDecoder.decode(trustData.trim))
        file ← writeToTempFile(dataBytes, ".p7b")
      } yield file

      result match {
        case Success(customTrustFile) ⇒
          logger.info(s"Successfully wrote custom truststore to file: ${customTrustFile.getAbsolutePath}")
          val keystore = KeyStore.getInstance(KeyStore.getDefaultType)
          keystore.load(new FileInputStream(cacertsPath), decryptedPass.toCharArray)

          val cf = CertificateFactory.getInstance("X.509")
          val bais = fullStream(customTrustFile)
          val certs = cf.generateCertificates(bais)

          if (certs.size() == 1) {
            logger.info("Truststore - One certificate found, no chain")
            val cert = cf.generateCertificate(bais)
            keystore.setCertificateEntry("api.nsi.hts.esit", cert)
          }
          else {
            logger.info(s"Truststore - Certificate chain length: ${certs.size()}")
            certs.toArray[Certificate](new Array[Certificate](certs.size())).zipWithIndex.foreach {
              case (cert, i) =>
                val alias = cert.asInstanceOf[X509Certificate].getSubjectX500Principal.getName
                keystore.setCertificateEntry(alias, cert)
                logger.info(s"truststore - certificate at index $i is ${cert.toString}")
            }
          }

          // Save the new keystore contents
          keystore.store(new FileOutputStream(cacertsPath), decryptedPass.toCharArray)

        case Failure(error) ⇒
          logger.info(s"Error in truststore configuration: ${error.getMessage}", error)
          sys.error(s"Error in truststore configuration: ${error.getMessage}")
      }
    }.recover {
      case e =>
        logger.error(s"error during truststore setup:", e)
    }

  }

  private def fullStream(fileName: File) = {
    val fis = new FileInputStream(fileName)
    val dis = new DataInputStream(fis)
    val bytes = new Array[Byte](dis.available)
    dis.readFully(bytes)
    val bais = new ByteArrayInputStream(bytes)
    bais
  }

  def writeToTempFile(data: Array[Byte], ext: String = ".tmp") = Try {
    val file = File.createTempFile(getClass.getSimpleName, ext)
    file.deleteOnExit()
    val os = new FileOutputStream(file)
    os.write(data)
    os.flush()
    os.close()
    file
  }

  private def printCerts(keyStoreFile: File, ksType: String) = {
    logger.info(s"start printing $ksType certificates")
    val cf = CertificateFactory.getInstance("X.509")
    val bais = fullStream(keyStoreFile)
    val certs = cf.generateCertificates(bais)

    if (certs.size() == 1) {
      logger.info("One certificate found, no chain")
      val cert = cf.generateCertificate(bais)
      logger.info(s"certificate is ${cert.toString}")
    }
    else {
      logger.info(s"Certificate chain has length: ${certs.size()}")
      certs.toArray[Certificate](new Array[Certificate](certs.size())).zipWithIndex.foreach {
        case (cert, i) =>
          logger.info(s"certificate at index $i is ${cert.toString}")
      }
    }

    logger.info(s"end printing $ksType certificates")
  }

  private def createKeyStoreConfig(ks: KeyStoreConfig, data: String): KeyStoreConfig = {
    logger.info("Creating key store config")

    val result = for {
      dataBytes ← Try(Base64.getDecoder.decode(data.trim))
      file ← writeToTempFile(dataBytes)
    } yield file

    result match {
      case Success(keyStoreFile) ⇒
        logger.info(s"Successfully wrote keystore to file: ${keyStoreFile.getAbsolutePath}")

        val decryptedPass = ks.password
          .map(pass ⇒ Base64.getDecoder.decode(pass))
          .map(bytes ⇒ new String(bytes))

        printCerts(keyStoreFile, "keystore")

        ks.copy(data = None, filePath = Some(keyStoreFile.getAbsolutePath), storeType = ks.storeType, password = decryptedPass)

      case Failure(error) ⇒
        logger.info(s"Error in keystore configuration: ${error.getMessage}", error)
        sys.error(s"Error in keystore configuration: ${error.getMessage}")
    }
  }

  private def createTrustStoreConfig(ts: TrustStoreConfig, data: String): TrustStoreConfig = {

    logger.info("Creating truststore config")

    val result = for {
      dataBytes ← Try(Base64.getDecoder.decode(data))
      file ← writeToTempFile(dataBytes, ".p7b")
    } yield file

    result match {
      case Success(trustStoreFile) ⇒
        logger.info(s"Successfully wrote truststore to file: ${trustStoreFile.getAbsolutePath}")
        ts.copy(filePath = Some(trustStoreFile.getAbsolutePath), data = None)

      case Failure(error) ⇒
        logger.info(s"Error in truststore configuration: ${error.getMessage}", error)
        sys.error(s"Error in truststore configuration: ${error.getMessage}")
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