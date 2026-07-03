package jp.shiguredo.sora.sdk.channel.tls

import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
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
 * 指定した CA 証明書のみを利用する経路、証明書検証を無効化する経路をまとめて扱います。
 */
internal object TlsConfigFactory {
    private const val CLIENT_CERTIFICATE_ALIAS = "client-certificate"

    /**
     * Android OS の既定の CA 証明書を利用する `X509TrustManager` を生成します。
     */
    fun createSystemTrustManager(): X509TrustManager {
        val trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)

        return trustManagerFactory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()
            ?: throw IllegalStateException("failed to obtain X509TrustManager")
    }

    /**
     * 指定した CA 証明書のみを利用する `X509TrustManager` を生成します。
     */
    fun createCustomCaTrustManager(caCertificate: X509Certificate): X509TrustManager {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setCertificateEntry("custom-ca", caCertificate)

        val trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)

        return trustManagerFactory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()
            ?: throw IllegalStateException("failed to obtain X509TrustManager")
    }

    /**
     * 指定した CA 証明書のみを利用する TLS ソケット設定を生成します。
     */
    fun createCustomCaTlsSocketConfig(caCertificate: X509Certificate): TlsSocketConfig {
        val trustManager = createCustomCaTrustManager(caCertificate)

        return TlsSocketConfig(
            trustManager = trustManager,
            sslSocketFactory = createSslSocketFactory(trustManager = trustManager),
        )
    }

    /**
     * クライアント証明書チェーンを利用する TLS ソケット設定を生成します。
     *
     * サーバー証明書検証には Android OS の既定の CA 証明書を利用します。
     */
    fun createClientAuthenticationTlsSocketConfig(
        clientCertificate: List<X509Certificate>,
        clientPrivateKey: PrivateKey,
    ): TlsSocketConfig {
        val trustManager = createSystemTrustManager()

        return TlsSocketConfig(
            trustManager = trustManager,
            sslSocketFactory =
                createSslSocketFactory(
                    trustManager = trustManager,
                    keyManagers = createClientAuthenticationKeyManagers(clientCertificate, clientPrivateKey),
                ),
        )
    }

    /**
     * 指定した CA 証明書とクライアント証明書チェーンを併用する TLS ソケット設定を生成します。
     */
    fun createCustomCaWithClientAuthenticationTlsSocketConfig(
        caCertificate: X509Certificate,
        clientCertificate: List<X509Certificate>,
        clientPrivateKey: PrivateKey,
    ): TlsSocketConfig {
        val trustManager = createCustomCaTrustManager(caCertificate)

        return TlsSocketConfig(
            trustManager = trustManager,
            sslSocketFactory =
                createSslSocketFactory(
                    trustManager = trustManager,
                    keyManagers = createClientAuthenticationKeyManagers(clientCertificate, clientPrivateKey),
                ),
        )
    }

    /**
     * 証明書検証とホスト名検証を無効化する TLS ソケット設定を生成します。
     *
     * クライアント証明書を指定しない場合は全引数を省略（デフォルトの `null`）で呼び出します。
     * クライアント証明書を指定する場合は `clientCertificate` と
     * 対応する `clientPrivateKey` をセットで指定する必要があります。
     * なお、`clientPrivateKey` が対で指定されているかのチェックはこのメソッドでは行わず、
     * 上位レイヤ（`SoraMediaChannel`、`PeerNetworkConfig`）で実施します。
     */
    fun createInsecureTlsSocketConfig(
        clientCertificate: List<X509Certificate>? = null,
        clientPrivateKey: PrivateKey? = null,
    ): TlsSocketConfig {
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
            sslSocketFactory =
                createSslSocketFactory(
                    trustManager = trustManager,
                    keyManagers =
                        if (clientCertificate != null && clientPrivateKey != null) {
                            createClientAuthenticationKeyManagers(clientCertificate, clientPrivateKey)
                        } else {
                            null
                        },
                ),
            hostnameVerifier = HostnameVerifier { _, _ -> true },
        )
    }

    /**
     * 指定された TrustManager と KeyManager から `SSLSocketFactory` を生成します。
     */
    private fun createSslSocketFactory(
        trustManager: X509TrustManager,
        keyManagers: Array<KeyManager>? = null,
    ): SSLSocketFactory {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagers, arrayOf(trustManager), null)
        return sslContext.socketFactory
    }

    /**
     * クライアント認証で利用する `KeyManager` を生成します。
     */
    private fun createClientAuthenticationKeyManagers(
        clientCertificate: List<X509Certificate>,
        clientPrivateKey: PrivateKey,
    ): Array<KeyManager> {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry(
            CLIENT_CERTIFICATE_ALIAS,
            clientPrivateKey,
            null,
            clientCertificate.toTypedArray(),
        )

        val keyManagerFactory =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, null)

        return keyManagerFactory.keyManagers
    }
}
