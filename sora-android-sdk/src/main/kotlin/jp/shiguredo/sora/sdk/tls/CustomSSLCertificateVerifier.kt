package jp.shiguredo.sora.sdk.tls

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.SSLCertificateVerifier
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * TURN-TLS のサーバ証明書を、指定した CA 証明書で検証する Verifier。
 *
 * @param caCertificate   X.509 形式 (PEM / DER どちらでも可) の CA 証明書
 * @param insecure        すべての証明書を信頼するかどうか
 *
 * @throws NoSuchElementException 初期化時に X509TrustManager が取得できなかった場合
 */
class CustomSSLCertificateVerifier(
    private val caCertificate: X509Certificate? = null,
    private val insecure: Boolean = false,
) : SSLCertificateVerifier {

    companion object {
        private val TAG = CustomSSLCertificateVerifier::class.simpleName
    }

    /**
     * コンストラクタ実行時に TrustManager を生成して保持しておく。
     * insecure が true の場合は、null にする。
     *
     * CustomSSLCertificateVerifier の build は例外をスローする可能性があるため、
     * CustomSSLCertificateVerifier のインスタンスを生成する際に、例外処理を行う。
     */
    private val trustManager: X509TrustManager?

    init {
        if (caCertificate == null && !insecure) {
            throw IllegalArgumentException("caCertificate is null and insecure is false")
        }

        if (insecure) {
            SoraLogger.i(TAG, "insecure is true. all certificates are trusted")
        }

        trustManager = if (caCertificate != null) {
            CustomX509TrustManagerBuilder(caCertificate).build()
        } else {
            null
        }
    }

    override fun verify(cert: ByteArray?): Boolean {

        if (insecure) {
            // insecure が true の場合は、すべての証明書を信頼する
            SoraLogger.d(TAG, "verify return true. because insecure is true")
            return true
        }

        // TODO(zztkm): caCertificate がユーザー指定されていない場合は、OS の CA 証明書を使うように実装する
        // 例 LetsEncrypt の証明書を検証する場合など
        if (cert == null) {
            SoraLogger.w(TAG, "verify return false. because cert is null")
            return false
        }

        return try {
            // DER → X509Certificate に変換
            val serverCert = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(cert)) as X509Certificate

            // 有効期限チェック
            serverCert.checkValidity()

            // チェーン検証
            trustManager?.checkServerTrusted(
                arrayOf(serverCert),
                serverCert.publicKey.algorithm
            )
            true
        } catch (e: Exception) {
            // 例外＝検証失敗
            SoraLogger.e(TAG, "verify return false. because certificate is invalid", e)
            false
        }
    }
}
