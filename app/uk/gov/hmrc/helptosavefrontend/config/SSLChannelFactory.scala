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

import java.security.SecureRandom
import javax.net.ssl.{SSLContext, SSLParameters}

import org.jboss.netty.channel.ChannelPipeline
import org.jboss.netty.channel.socket.SocketChannel
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.handler.ssl.SslHandler
import play.api.Logger

class SSLChannelFactory extends NioClientSocketChannelFactory {

  override def newChannel(pipeline: ChannelPipeline): SocketChannel = {

    Logger.info("creating a new ssl socket channel")

    val sSLParameters = new SSLParameters()
    sSLParameters.setEndpointIdentificationAlgorithm("HTTPS")

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(Array.empty, TrustAllManager.trustAllCerts, new SecureRandom())
    val sslEngine = sslContext.createSSLEngine()
    sslEngine.setUseClientMode(true)
    sslEngine.setSSLParameters(sSLParameters)
    pipeline.addLast("ssl", new SslHandler(sslEngine))
    super.newChannel(pipeline)
  }
}
