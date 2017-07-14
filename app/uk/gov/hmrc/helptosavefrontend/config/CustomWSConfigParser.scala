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
import java.security.cert.CertificateFactory
import java.util.Base64
import javax.inject.{Inject, Singleton}
import javax.net.ssl.{HostnameVerifier, HttpsURLConnection}

import play.api.inject.{Binding, Module}
import play.api.libs.ws.ssl.{KeyStoreConfig, TrustStoreConfig}
import play.api.libs.ws.{WSClientConfig, WSConfigParser}
import play.api.{Configuration, Environment, Logger}

import scala.util.{Failure, Success, Try}

@Singleton
class CustomWSConfigParser @Inject()(configuration: Configuration, env: Environment) extends WSConfigParser(configuration, env) {

  import javax.net.ssl.SSLSession

  val nsiHostVerifier = new HostnameVerifier {
    override def verify(hostName: String, sslSession: SSLSession): Boolean = {
      Logger.info(s"sslSession peerHost = ${sslSession.getPeerHost}")
      Logger.info(s"sslSession PeerCertificateChain = ${sslSession.getPeerCertificateChain}")
      Logger.info(s"sslSesion hostname=$hostName")
      if (hostName.equals("api.nsi.hts.esit")) {
        true
      }
      false
    }
  }

  HttpsURLConnection.setDefaultHostnameVerifier(nsiHostVerifier)

  Logger.info("Starting CustomWSConfigParser")

  override def parse(): WSClientConfig = {
    Logger.info("Parsing WSClientConfig")
    val internalParser = new WSConfigParser(configuration, env)
    val config = internalParser.parse()

    val keyStores = config.ssl.keyManagerConfig.keyStoreConfigs.filter(_.data.forall(_.nonEmpty)).map { ks ⇒
      (ks.storeType.toUpperCase, ks.filePath, ks.data) match {
        // it's a PEM, so we don't need to do anything
        case ("PEM", _, _) ⇒
          Logger.info("Adding PEM keystore certificate")
          ks

        // it is not a PEM and data has been provided but no file path given, therefore assume data is base64 encoded file
        case (_, None, Some(data)) ⇒
          createKeyStoreConfig(ks, data)

        // just because ...
        case other ⇒
          Logger.info(s"Adding ${other._1} type keystore")
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
      val cacertsPass = config.getString("truststore.cacerts.password").get

      val decryptedPass = new String(Base64.getDecoder.decode(cacertsPass))

      val trustData = config.getString("truststore.data").get

      val result = for {
        dataBytes ← Try(Base64.getDecoder.decode(trustData))
        file ← writeToTempFile(dataBytes, ".p7b")
      } yield file

      result match {
        case Success(customTrustFile) ⇒
          Logger.info(s"Successfully wrote custom truststore to file: ${customTrustFile.getAbsolutePath}")

          val is = new FileInputStream(cacertsPath)

          val keystore = KeyStore.getInstance(KeyStore.getDefaultType)
          keystore.load(is, decryptedPass.toCharArray)

          val cf = CertificateFactory.getInstance("X.509")
          val fis = new FileInputStream(customTrustFile)
          val dis = new DataInputStream(fis)
          val bytes = new Array[Byte](dis.available)
          dis.readFully(bytes)
          val bais = new ByteArrayInputStream(bytes)
          val certs = cf.generateCertificate(bais)

          keystore.setCertificateEntry("api.nsi.hts.esit", certs)

          // Save the new keystore contents
          val out = new FileOutputStream(cacertsPath)
          keystore.store(out, decryptedPass.toCharArray)

        case Failure(error) ⇒
          Logger.info(s"Error in truststore configuration: ${error.getMessage}", error)
          sys.error(s"Error in truststore configuration: ${error.getMessage}")
      }
    }.recover {
      case e => Logger.error(s"error during truststore setup ${e.printStackTrace()}")
    }

  }

  def writeToTempFile(data: Array[Byte], ext: String = ".tmp"): Try[File] = Try {
    val file = File.createTempFile(getClass.getSimpleName, ext)
    file.deleteOnExit()
    val os = new FileOutputStream(file)
    os.write(data)
    os.flush()
    os.close()
    file
  }

  private def createKeyStoreConfig(ks: KeyStoreConfig, data: String): KeyStoreConfig = {
    Logger.info("Creating key store config")

    val result = for {
      dataBytes ← Try(Base64.getDecoder.decode(data))
      file ← writeToTempFile(dataBytes)
    } yield file

    result match {
      case Success(keyStoreFile) ⇒
        Logger.info(s"Successfully wrote keystore to file: ${keyStoreFile.getAbsolutePath}")

        val decryptedPass = ks.password
          .map(pass ⇒ Base64.getDecoder.decode(pass))
          .map(bytes ⇒ new String(bytes))

        ks.copy(data = None, filePath = Some(keyStoreFile.getAbsolutePath), storeType = ks.storeType, password = decryptedPass)

      case Failure(error) ⇒
        Logger.info(s"Error in keystore configuration: ${error.getMessage}", error)
        sys.error(s"Error in keystore configuration: ${error.getMessage}")
    }
  }

  private def createTrustStoreConfig(ts: TrustStoreConfig, data: String): TrustStoreConfig = {

    Logger.info("Creating truststore config")

    val result = for {
      dataBytes ← Try(Base64.getDecoder.decode(data))
      file ← writeToTempFile(dataBytes, ".p7b")
    } yield file

    result match {
      case Success(trustStoreFile) ⇒
        Logger.info(s"Successfully wrote truststore to file: ${trustStoreFile.getAbsolutePath}")
        ts.copy(filePath = Some(trustStoreFile.getAbsolutePath), data = None)

      case Failure(error) ⇒
        Logger.info(s"Error in truststore configuration: ${error.getMessage}", error)
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