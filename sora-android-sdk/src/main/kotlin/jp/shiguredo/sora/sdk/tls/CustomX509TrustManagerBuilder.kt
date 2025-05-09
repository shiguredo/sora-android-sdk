package jp.shiguredo.sora.sdk.tls

import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * CustomTrustManagerBuilder は、指定の CA 証明書を使用して TLS 接続を行うための
 * カスタムされた TrustManager を構築するためのクラスです。
 */
class CustomX509TrustManagerBuilder(
    /**
     * CA 証明書を指定します。
     */
    private val caCertificate: X509Certificate
) {
    /**
     * CA 証明書を使用して TLS 接続を行うためのカスタムされた TrustManager を構築します。
     *
     * @return 指定された CA 証明書を使用する X509TrustManager
     */
    fun build(): X509TrustManager {
        // 空の KeyStore を用意し、指定された CA 証明書を登録
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("custom_ca", caCertificate)
        }

        // keyStore を使用して TrustManagerFactory を初期化
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        ).apply {
            init(keyStore)
        }

        // X509TrustManager を取り出す
        return trustManagerFactory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()
    }
}
