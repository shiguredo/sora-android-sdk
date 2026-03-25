package jp.shiguredo.sora.sdk.channel.tls

import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal data class TlsSocketConfig(
    val trustManager: X509TrustManager,
    val sslSocketFactory: SSLSocketFactory,
    val hostnameVerifier: HostnameVerifier? = null,
)

internal object TlsConfigFactory {
    fun createTrustManager(caCertificate: X509Certificate?): X509TrustManager {
        val defaultTrustManager = createDefaultTrustManager()
        if (caCertificate == null) {
            return defaultTrustManager
        }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)

        defaultTrustManager.acceptedIssuers.forEachIndexed { index, certificate ->
            keyStore.setCertificateEntry("system-ca-$index", certificate)
        }
        keyStore.setCertificateEntry("custom-ca", caCertificate)

        val trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)

        return trustManagerFactory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()
            ?: throw IllegalStateException("X509TrustManager を取得できませんでした")
    }

    fun createTlsSocketConfig(caCertificate: X509Certificate): TlsSocketConfig {
        val trustManager = createTrustManager(caCertificate)

        return TlsSocketConfig(
            trustManager = trustManager,
            sslSocketFactory = createSslSocketFactory(trustManager),
        )
    }

    fun createInsecureTlsSocketConfig(): TlsSocketConfig {
        val trustManager =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) {
                }

                override fun checkServerTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) {
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }

        return TlsSocketConfig(
            trustManager = trustManager,
            sslSocketFactory = createSslSocketFactory(trustManager),
            hostnameVerifier = HostnameVerifier { _, _ -> true },
        )
    }

    private fun createSslSocketFactory(trustManager: X509TrustManager): SSLSocketFactory {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        return sslContext.socketFactory
    }

    private fun createDefaultTrustManager(): X509TrustManager {
        val trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)

        return trustManagerFactory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()
            ?: throw IllegalStateException("X509TrustManager を取得できませんでした")
    }
}
