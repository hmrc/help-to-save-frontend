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

import java.nio.charset.StandardCharsets
import java.util
import java.util.Base64
import javax.inject.{Inject, Singleton}

import com.typesafe.config.{ConfigObject, ConfigValueFactory}
import play.api.inject.{Binding, Module}
import play.api.libs.ws.ssl.{KeyStoreConfig, TrustStoreConfig}
import play.api.libs.ws.{WSClientConfig, WSConfigParser}
import play.api.{Configuration, Environment}

@Singleton
class CustomWSConfigParser @Inject()(configuration: Configuration, env: Environment) extends WSConfigParser(configuration, env) {

  override def parse(): WSClientConfig = {
    val mergedConfiguration = mergeAllStores(configuration)
    val internalParser = new WSConfigParser(mergedConfiguration, env)
    val config = internalParser.parse()
    val keystores = config.ssl.keyManagerConfig.keyStoreConfigs.filter(_.data.forall(_.nonEmpty)).map { ks ⇒
      (ks.storeType.toUpperCase, ks.filePath, ks.data) match {
        // it's a PEM, so we don't need to do anything
        case ("PEM", _, _) ⇒ ks
        // it is not a PEM and data has been provided but no file path given, therefore assume data is base64 encoded file
        case (_, None, Some(data)) ⇒ createKeyStoreConfig(ks, data)
        // just because ...
        case _ ⇒ ks
      }
    }

    val truststores = config.ssl.trustManagerConfig.trustStoreConfigs.map { ts ⇒
      (ts.storeType.toUpperCase, ts.filePath, ts.data) match {
        case ("PEM", _, _) ⇒ ts
        case (_, None, Some(_)) ⇒ createTrustStoreConfig(ts)
        case _ ⇒ ts
      }
    }

    val modded = config.copy(
      ssl = config.ssl.copy(
        keyManagerConfig = config.ssl.keyManagerConfig.copy(
          keyStoreConfigs = keystores
        ),
        trustManagerConfig = config.ssl.trustManagerConfig.copy(
          trustStoreConfigs = truststores
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

  private def createKeyStoreConfig(ks: KeyStoreConfig, data: String): KeyStoreConfig = {
    val decoded = Base64.getDecoder.decode(data)
    ks.copy(data = Some(new String(decoded, StandardCharsets.US_ASCII)), storeType = "PEM")
  }

  private def createTrustStoreConfig(ts: TrustStoreConfig): TrustStoreConfig = {
    val decoded = Base64.getDecoder.decode(ts.data.get)
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