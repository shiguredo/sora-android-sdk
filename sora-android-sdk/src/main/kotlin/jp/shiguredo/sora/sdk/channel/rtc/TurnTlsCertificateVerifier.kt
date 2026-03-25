package jp.shiguredo.sora.sdk.channel.rtc

import jp.shiguredo.sora.sdk.channel.tls.TlsConfigFactory
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.SSLCertificateVerifier
import java.io.ByteArrayInputStream
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.CertificateParsingException
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * libwebrtc の `SSLCertificateVerifier` として TURN-TLS のサーバー証明書チェーンを検証します。
 *
 * 検証には Android OS の CA 証明書を利用し、`caCertificate` が指定された場合は
 * 追加の CA 証明書もあわせて利用します。
 */
internal class TurnTlsCertificateVerifier(
    private val insecure: Boolean,
    private val caCertificate: X509Certificate? = null,
) : SSLCertificateVerifier {
    companion object {
        private val TAG = TurnTlsCertificateVerifier::class.simpleName
        private const val OID_SERVER_AUTH = "1.3.6.1.5.5.7.3.1"
        private const val OID_ANY_EXTENDED_KEY_USAGE = "2.5.29.37.0"
    }

    private val trustManager: X509TrustManager =
        if (caCertificate == null) {
            TlsConfigFactory.createSystemTrustManager()
        } else {
            TlsConfigFactory.createCustomCaTrustManager(caCertificate).also {
                SoraLogger.i(TAG, "custom CA certificate has been added for TURN-TLS")
            }
        }

    // verifyChain を実装している場合は verifyChain が呼び出されるため
    // verify は基本的に利用しない想定になっています。
    override fun verify(certificate: ByteArray): Boolean = verifyChain(arrayOf(certificate))

    override fun verifyChain(certificateChain: Array<ByteArray>): Boolean {
        // PeerConnection.IceServer に TLS_CERT_POLICY_INSECURE_NO_CHECK が設定されていたとしても
        // この verifier が呼び出されるため、余計な検証処理とログ出力を抑制するためにここでスキップする。
        if (insecure) {
            return true
        }

        if (certificateChain.isEmpty()) {
            SoraLogger.w(TAG, "verifyChain() に空の証明書チェーンが渡されました")
            return false
        }

        return runCatching {
            val certificateFactory =
                CertificateFactory.getInstance("X.509")
            val certificates =
                certificateChain
                    .map { certificate ->
                        certificateFactory.generateCertificate(ByteArrayInputStream(certificate)) as X509Certificate
                    }
            val certPath = certificateFactory.generateCertPath(certificates)
            val trustAnchors =
                trustManager.acceptedIssuers
                    .map { certificate -> TrustAnchor(certificate, null) }
                    .toSet()
            if (trustAnchors.isEmpty()) {
                throw IllegalStateException("TrustAnchor を構築できませんでした")
            }

            // libwebrtc の既定挙動に合わせるため、失効確認は行わない。
            // libwebrtc 側の公開 API では `api/peer_connection_interface.h` の
            // `TlsCertPolicy::kTlsCertPolicySecure` により TLS-TURN の証明書検証を有効化するが、
            // 失効確認までは規定していない。
            // また、下位の BoringSSL 側でも
            // https://boringssl.googlesource.com/boringssl/+/master/pki/verify_certificate_chain.h
            // に `No revocation checking is performed.` とあり、
            // CRL / OCSP の失効確認は既定では行われない。
            val pkixParameters =
                PKIXParameters(trustAnchors).apply {
                    isRevocationEnabled = false
                }
            CertPathValidator.getInstance("PKIX").validate(certPath, pkixParameters)
            if (!verifyExtendedKeyUsage(certificates)) {
                throw IllegalStateException("TLS サーバー証明書の EKU 検証に失敗しました")
            }
            true
        }.getOrElse { error ->
            SoraLogger.e(TAG, "証明書チェーンの検証に失敗しました: ${error.message}", error)
            false
        }
    }

    private fun verifyExtendedKeyUsage(certificates: List<X509Certificate>): Boolean =
        try {
            certificates.all { certificate ->
                val extendedKeyUsage = certificate.extendedKeyUsage ?: return@all true
                extendedKeyUsage.contains(OID_SERVER_AUTH) ||
                    extendedKeyUsage.contains(OID_ANY_EXTENDED_KEY_USAGE)
            }
        } catch (error: CertificateParsingException) {
            SoraLogger.w(TAG, "EKU の解析に失敗しました: ${error.message}")
            false
        }
}
