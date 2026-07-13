package jp.shiguredo.sora.sdk.channel.tls

import android.util.Base64
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * PEM 文字列を Java の型オブジェクト (X509Certificate / PrivateKey) へ変換するユーティリティ。
 *
 * `SoraMediaChannel` の公開 API で受け取る PEM 文字列を、内部コンポーネントが扱う
 * `X509Certificate` / `PrivateKey` へ変換するために利用する。
 */
internal object PemDecoder {
    // PKCS#8 秘密鍵の PEM ヘッダ / フッタ
    private const val PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----"
    private const val PKCS8_FOOTER = "-----END PRIVATE KEY-----"

    // PKCS#8 DER の秘密鍵解析で試行する鍵アルゴリズム
    // TLS クライアント証明書で一般的な RSA と EC を順に試す
    private val PRIVATE_KEY_ALGORITHMS = listOf("RSA", "EC")

    /**
     * PEM 文字列から単一の [X509Certificate] を生成する。
     *
     * X.509 の `CertificateFactory` は `-----BEGIN CERTIFICATE-----` /
     * `-----END CERTIFICATE-----` を含む PEM をそのまま解釈できるため、
     * ヘッダ除去や Base64 デコードは行わない。
     *
     * この関数は証明書が「ちょうど 1 個」であることを要求する。CA 証明書のように単数を
     * 前提とする用途で、複数証明書を連結した PEM (チェーン / バンドル) を誤って渡した場合に、
     * 先頭のみを黙って採用して trust anchor を取り違えることを防ぐため、複数含む PEM は拒否する。
     *
     * @throws IllegalArgumentException PEM の形式が不正、証明書のパースに失敗、
     *   または証明書がちょうど 1 個でない (0 個または複数) 場合
     */
    fun decodeCertificate(pem: String): X509Certificate {
        val certificates =
            try {
                val certificateFactory = CertificateFactory.getInstance("X.509")
                ByteArrayInputStream(pem.toByteArray(Charsets.UTF_8)).use { input ->
                    certificateFactory.generateCertificates(input).map { it as X509Certificate }
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("failed to parse PEM certificate", e)
            }
        require(certificates.size == 1) {
            "PEM must contain exactly one certificate but contained ${certificates.size}"
        }
        return certificates.first()
    }

    /**
     * PEM 文字列から [X509Certificate] のリスト (証明書チェーン) を生成する。
     *
     * 単一証明書・複数証明書を連結した PEM の両方に対応する。
     * 証明書が 1 つも得られなかった場合は例外を送出する。
     *
     * @throws IllegalArgumentException PEM の形式が不正、証明書のパースに失敗、
     *   または証明書が 1 つも含まれていない場合
     */
    fun decodeCertificateChain(pem: String): List<X509Certificate> {
        val certificates =
            try {
                val certificateFactory = CertificateFactory.getInstance("X.509")
                ByteArrayInputStream(pem.toByteArray(Charsets.UTF_8)).use { input ->
                    certificateFactory.generateCertificates(input).map { it as X509Certificate }
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("failed to parse PEM certificate chain", e)
            }
        require(certificates.isNotEmpty()) {
            "PEM certificate chain must contain at least one certificate"
        }
        return certificates
    }

    /**
     * PKCS#8 PEM 文字列から [PrivateKey] を生成する。
     *
     * 対応フォーマットは PKCS#8 (`-----BEGIN PRIVATE KEY-----`) のみである。
     * PKCS#1 (`-----BEGIN RSA PRIVATE KEY-----`) や SEC1 (`-----BEGIN EC PRIVATE KEY-----`) など
     * 他の形式は非対応とする。
     *
     * 証明書と異なり JCA には PEM 秘密鍵を直接読む API が無いため、
     * ヘッダ・フッタを除去して Base64 デコードした PKCS#8 DER から鍵を生成する。
     *
     * @throws IllegalArgumentException PKCS#8 以外の形式、Base64 デコード失敗、
     *   または秘密鍵のパースに失敗した場合
     */
    fun decodePkcs8PrivateKey(pem: String): PrivateKey {
        val normalized = pem.trim()
        require(normalized.contains(PKCS8_HEADER) && normalized.contains(PKCS8_FOOTER)) {
            "client private key must be in PKCS#8 PEM format ($PKCS8_HEADER)"
        }

        // ヘッダ・フッタと改行・空白を取り除いて Base64 本体のみを抽出する
        val base64Body =
            normalized
                .substringAfter(PKCS8_HEADER)
                .substringBefore(PKCS8_FOOTER)
                .replace("\\s".toRegex(), "")

        val der =
            try {
                Base64.decode(base64Body, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("failed to Base64 decode PKCS#8 private key", e)
            }

        val keySpec = PKCS8EncodedKeySpec(der)
        for (algorithm in PRIVATE_KEY_ALGORITHMS) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(keySpec)
            } catch (e: Exception) {
                // このアルゴリズムでは解析できなかったため次のアルゴリズムを試す
            }
        }
        throw IllegalArgumentException(
            "failed to parse PKCS#8 private key with supported algorithms: $PRIVATE_KEY_ALGORITHMS",
        )
    }
}
