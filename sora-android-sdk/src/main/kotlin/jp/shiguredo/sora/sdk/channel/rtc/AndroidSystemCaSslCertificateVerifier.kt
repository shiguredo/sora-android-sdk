package jp.shiguredo.sora.sdk.channel.rtc

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.SSLCertificateVerifier
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Android OS の CA 証明書を使って TLS サーバー証明書チェーンを検証します。
 */
internal class AndroidSystemCaSslCertificateVerifier : SSLCertificateVerifier {
    companion object {
        private val TAG = AndroidSystemCaSslCertificateVerifier::class.simpleName
    }

    private val certificateFactory: CertificateFactory =
        CertificateFactory.getInstance("X.509")

    private val trustManager: X509TrustManager = createTrustManager()

    override fun verify(certificate: ByteArray): Boolean = verifyChain(arrayOf(certificate))

    override fun verifyChain(certificateChain: Array<ByteArray>): Boolean {
        if (certificateChain.isEmpty()) {
            SoraLogger.w(TAG, "verifyChain() に空の証明書チェーンが渡されました")
            return false
        }

        return runCatching {
            val x509Certificates =
                certificateChain
                    .map { certificate ->
                        certificateFactory.generateCertificate(ByteArrayInputStream(certificate)) as X509Certificate
                    }.toTypedArray()
            val authType = resolveAuthType(x509Certificates.first())
            trustManager.checkServerTrusted(x509Certificates, authType)
            true
        }.getOrElse { error ->
            SoraLogger.e(TAG, "証明書チェーンの検証に失敗しました: ${error.message}", error)
            false
        }
    }

    private fun resolveAuthType(certificate: X509Certificate): String =
        certificate.sigAlgName
            .substringAfter("with", certificate.sigAlgName)

    private fun createTrustManager(): X509TrustManager {
        val trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)

        return trustManagerFactory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()
            ?: throw IllegalStateException("X509TrustManager を取得できませんでした")
    }
}
