package uk.gov.hmrc.helptosavefrontend.config

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl._

import play.api.Logger

object TrustAllManager {

  import javax.net.ssl.SSLSession

  // Create a trust manager that does not validate certificate chains
  val trustAllCerts: Array[TrustManager] = Array[TrustManager](new X509TrustManager {
    override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {
      Logger.info("All Servers trusted")
    }

    override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {
      Logger.info("All Servers trusted")
    }

    override def getAcceptedIssuers: Array[X509Certificate] = {
      Array.empty
    }
  })

  // Install the all-trusting trust manager
  val sc: SSLContext = SSLContext.getInstance("SSL")
  sc.init(null, trustAllCerts, new SecureRandom())
  HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory)

  // Create all-trusting host name verifier
  val allHostsValid = new HostnameVerifier {
    override def verify(host: String, sslSession: SSLSession): Boolean = {
      Logger.info("verification done for host $host")
      true
    }
  }

  // Install the all-trusting host verifier
  HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)
}

