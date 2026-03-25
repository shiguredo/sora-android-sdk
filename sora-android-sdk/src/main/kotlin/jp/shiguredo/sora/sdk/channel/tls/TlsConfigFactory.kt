package jp.shiguredo.sora.sdk.channel.tls
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * OkHttpClient に適用する TLS 関連の設定です。
 *
 * `hostnameVerifier` は、ホスト名検証を上書きする必要がある場合にのみ利用します。
 */
internal data class TlsSocketConfig(
    val trustManager: X509TrustManager,
    val sslSocketFactory: SSLSocketFactory,
    val hostnameVerifier: HostnameVerifier? = null,
)

/**
 * Sora Android SDK で利用する TLS 関連設定を生成します。
 *
 * Android OS の既定の CA 証明書を利用する経路と、
 * 追加の CA 証明書を組み合わせる経路、証明書検証を無効化する経路をまとめて扱います。
 */
internal object TlsConfigFactory {
    /**
     * Android OS の既定の CA 証明書を利用する `X509TrustManager` を生成します。
     */
    fun createSystemTrustManager(): X509TrustManager = createDefaultTrustManager()

    /**
     * Android OS の既定の CA 証明書と追加の CA 証明書を併用する `X509TrustManager` を生成します。
     */
    fun createCustomCaTrustManager(caCertificate: X509Certificate): X509TrustManager {
        val defaultTrustManager = createDefaultTrustManager()

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)

        // acceptedIssuers はこの時点のシステム CA のスナップショットです。
        // そのため、この KeyStore を使う経路では、インスタンスを再作成し、
        // TrustManager を再生成するまでシステム CA の更新は反映されません。
        defaultTrustManager.acceptedIssuers.forEachIndexed { index, certificate ->
            keyStore.setCertificateEntry("system-ca-$index", certificate)
        }
        keyStore.setCertificateEntry("custom-ca", caCertificate)

        val trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)

        val trustManager =
            trustManagerFactory.trustManagers
                .filterIsInstance<X509TrustManager>()
                .firstOrNull()
                ?: throw IllegalStateException("failed to obtain X509TrustManager")

        return trustManager
    }

    /**
     * 追加の CA 証明書を利用する TLS ソケット設定を生成します。
     */
    fun createCustomCaTlsSocketConfig(caCertificate: X509Certificate): TlsSocketConfig {
        val trustManager = createCustomCaTrustManager(caCertificate)

        return TlsSocketConfig(
            trustManager = trustManager,
            sslSocketFactory = createSslSocketFactory(trustManager),
        )
    }

    /**
     * 証明書検証とホスト名検証を無効化する TLS ソケット設定を生成します。
     */
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

    /**
     * 指定された `TrustManager` から `SSLSocketFactory` を生成します。
     */
    private fun createSslSocketFactory(trustManager: X509TrustManager): SSLSocketFactory {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        return sslContext.socketFactory
    }

    /**
     * Android OS の既定の CA 証明書を利用する `X509TrustManager` を内部的に生成します。
     */
    private fun createDefaultTrustManager(): X509TrustManager {
        val trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)

        return trustManagerFactory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()
            ?: throw IllegalStateException("failed to obtain X509TrustManager")
    }
}
