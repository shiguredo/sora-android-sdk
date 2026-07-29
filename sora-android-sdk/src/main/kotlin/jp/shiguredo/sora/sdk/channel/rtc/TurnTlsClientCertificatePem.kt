package jp.shiguredo.sora.sdk.channel.rtc

import android.util.Base64
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * X509Certificate / PrivateKey を PEM 形式の文字列に変換するユーティリティ。
 *
 * TURN-TLS のクライアント証明書設定において、
 * libwebrtc の `setTlsClientCertificate` に PEM 文字列を渡すために使用する。
 */
internal object TurnTlsClientCertificatePem {
    private const val PEM_LINE_LENGTH = 64

    /**
     * 単一の X509Certificate を PEM 文字列に変換する。
     *
     * 出力は標準的な PEM 形式。
     */
    fun toCertificatePem(certificate: X509Certificate): String =
        buildPem(
            type = "CERTIFICATE",
            der = certificate.encoded,
        )

    /**
     * 証明書チェーン（複数の X509Certificate）を連結した PEM 文字列に変換する。
     *
     * 各証明書の PEM を単純に連結した文字列を返す。
     * 単一証明書として扱う場合は [toCertificatePem] を使用すること。
     */
    fun toCertificateChainPem(certificates: List<X509Certificate>): String =
        certificates.joinToString(separator = "") { certificate ->
            toCertificatePem(certificate)
        }

    /**
     * PrivateKey を PEM 文字列に変換する。
     *
     * 出力は標準的な PEM 形式。
     * libwebrtc の `SSLIdentity::CreateFromPEMChainStrings()` が対応している形式。
     */
    fun toPrivateKeyPem(privateKey: PrivateKey): String =
        buildPem(
            type = "PRIVATE KEY",
            der = privateKey.encoded,
        )

    /**
     * DER エンコードされたバイト列から PEM 形式の文字列を組み立てる。
     *
     * 1. DER バイト列を Base64 エンコードする
     * 2. 64 文字ごとに改行を挿入する
     * 3. PEM の開始/終了文字列で囲む
     */
    private fun buildPem(
        type: String,
        der: ByteArray,
    ): String {
        val base64 =
            Base64
                .encodeToString(der, Base64.NO_WRAP)
                .chunked(PEM_LINE_LENGTH)
                .joinToString(separator = "\n")
        return "-----BEGIN $type-----\n$base64\n-----END $type-----\n"
    }
}

/**
 * リフレクションで libwebrtc の `PeerConnection.IceServer.Builder.setTlsClientCertificate` を呼び出す。
 *
 * このメソッドは Shiguredo パッチ (`android_turn_tls_client_certificate.patch`) で追加された API であり、
 * 標準の libwebrtc 公開 API には存在しない。
 *
 * `certificatePem` には単一証明書の PEM と証明書チェーン（連結した PEM）の両方を指定できる。
 */
internal object TurnTlsClientCertificateConfigurer {
    private const val METHOD_NAME = "setTlsClientCertificate"

    fun applyToIceServerBuilder(
        builder: Any,
        privateKeyPem: String,
        certificatePem: String,
    ) {
        val method =
            builder.javaClass.methods.firstOrNull { candidate ->
                candidate.name == METHOD_NAME &&
                    candidate.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java))
            }
                ?: throw IllegalStateException(
                    "libwebrtc does not support TURN-TLS client certificates or certificate chains. " +
                        "Apply android_turn_tls_client_certificate.patch to the Android build.",
                )
        try {
            method.invoke(builder, privateKeyPem, certificatePem)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw IllegalStateException(
                "Failed to set TURN-TLS client certificate. " +
                    "The provided private key or certificate PEM may be invalid.",
                e.cause,
            )
        }
    }
}
