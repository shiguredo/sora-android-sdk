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
    private val caCertificate: X509Certificate,

    /**
     * すべての証明書を信頼するかどうかを指定します。
     *
     * デフォルトは false です。
     */
    private val insecure: Boolean = false,
) {
    /**
     * CA 証明書を使用して TLS 接続を行うためのカスタムされた TrustManager を構築します。
     *
     * @return 指定された CA 証明書を使用する X509TrustManager
     */
    fun build(): X509TrustManager {
        // insecure が true の場合、すべての証明書を信頼する TrustManager を返す
        if (insecure) {
             return object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        }

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
        // 万が一、X509TrustManager が見つからない場合は、NoSuchElementException がスローされる
        return trustManagerFactory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()
    }
}
