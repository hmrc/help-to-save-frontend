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
