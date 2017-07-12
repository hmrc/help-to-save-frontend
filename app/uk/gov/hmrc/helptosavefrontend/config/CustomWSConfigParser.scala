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

import java.io.{File, FileOutputStream}
import java.util
import java.util.Base64
import javax.inject.{Inject, Singleton}

import com.typesafe.config.{ConfigObject, ConfigValueFactory}
import play.api.inject.{Binding, Module}
import play.api.libs.ws.ssl.{KeyStoreConfig, TrustStoreConfig}
import play.api.libs.ws.{WSClientConfig, WSConfigParser}
import play.api.{Configuration, Environment, Logger}

import scala.util.{Failure, Success, Try}

@Singleton
class CustomWSConfigParser @Inject()(configuration: Configuration, env: Environment) extends WSConfigParser(configuration, env) {

  Logger.info("Starting CustomWSConfigParser")

  override def parse(): WSClientConfig = {
    Logger.info("Parsing WSClientConfig")
    val mergedConfiguration = mergeAllStores(configuration)
    val internalParser = new WSConfigParser(mergedConfiguration, env)
    val config = internalParser.parse()

    val keyStores = config.ssl.keyManagerConfig.keyStoreConfigs.filter(_.data.forall(_.nonEmpty)).map { ks ⇒
      (ks.storeType.toUpperCase, ks.filePath, ks.data) match {
        // it's a PEM, so we don't need to do anything
        case ("PEM", _, _) ⇒
          Logger.info("Adding PEM keystore certificate")
          ks

        // it is not a PEM and data has been provided but no file path given, therefore assume data is base64 encoded file
        case (_, None, Some(data)) ⇒
          Logger.info(s"loading keystore from data")
          createKeyStoreConfig(ks, data)

        // just because ...
        case other ⇒
          Logger.info(s"Adding ${other._1} type keystore")
          ks
      }
    }

    val trustStores = config.ssl.trustManagerConfig.trustStoreConfigs.map { ts ⇒
      (ts.storeType.toUpperCase, ts.filePath, ts.data) match {
        case ("PEM", _, _) ⇒
          Logger.info("Adding PEM truststore")
          ts
        case (storeType, None, Some(data)) ⇒
          Logger.info(s"Adding $storeType truststore")
          createTrustStoreConfig(ts, data)

        case other ⇒
          Logger.info(s"Adding ${other._1} type truststore")
          Logger.info(s"java.home is ${sys.env.get("java.home")}")
          ts
      }
    }

    val modded = config.copy(
      ssl = config.ssl.copy(
        keyManagerConfig = config.ssl.keyManagerConfig.copy(
          keyStoreConfigs = keyStores
        ),
        trustManagerConfig = config.ssl.trustManagerConfig.copy(
          trustStoreConfigs = trustStores
        )
      )
    )
    modded
  }

  private def mergeAllStores(config: Configuration): Configuration = {
    mergeStores(mergeStores(config, "key"), "trust")
  }

  private def mergeStores(config: Configuration, name: String): Configuration = {
    val under = config.underlying
    if (under.hasPath(s"play.ws.ssl.${name}Manager.store")) {
      val singleStore: ConfigObject = under.getObject(s"play.ws.ssl.${name}Manager.store")
      val stores: util.List[AnyRef] = under.getList(s"play.ws.ssl.${name}Manager.stores").unwrapped()
      if (singleStore != null) {
        stores.add(singleStore)
      }
      config.copy(underlying = config.underlying.withValue(s"play.ws.ssl.${name}Manager.stores", ConfigValueFactory.fromIterable(stores)))
    } else config
  }


  def writeToTempFile(data: Array[Byte]): Try[File] = Try {
    val file = File.createTempFile(getClass.getSimpleName, ".tmp")
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
    val decoded = Base64.getDecoder.decode(data)
    ts.storeType match {
      case "base64-PEM" => ts.copy(data = Some(new String(decoded)), storeType = "PEM")
      case _ => ts
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