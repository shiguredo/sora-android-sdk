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
 *
 * @throws NoSuchElementException 初期化時に X509TrustManager が取得できなかった場合
 */
class CustomSSLCertificateVerifier(
    private val caCertificate: X509Certificate
) : SSLCertificateVerifier {

    /**
     * コンストラクタ実行時に TrustManager を生成して保持しておく。
     *
     * CustomSSLCertificateVerifier の build は例外をスローする可能性があるため、
     * CustomSSLCertificateVerifier のインスタンスを生成する際に、例外処理を行う。
     */
    private val trustManager: X509TrustManager =
        CustomX509TrustManagerBuilder(caCertificate).build()

    override fun verify(cert: ByteArray?): Boolean {
        // TODO(zztkm): caCertificate がユーザー指定されていない場合は、OS の CA 証明書を使うように実装する
        // 例 LetsEncrypt の証明書を検証する場合など
        if (cert == null) {
            SoraLogger.w("CustomSSLCertificateVerifier", "cert is null")
            return false
        }

        return try {
            // DER → X509Certificate に変換
            val serverCert = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(cert)) as X509Certificate

            // 有効期限チェック
            serverCert.checkValidity()

            // チェーン検証
            trustManager.checkServerTrusted(
                arrayOf(serverCert),
                serverCert.publicKey.algorithm
            )
            true
        } catch (e: Exception) {
            // 例外＝検証失敗
            false
        }
    }
}
