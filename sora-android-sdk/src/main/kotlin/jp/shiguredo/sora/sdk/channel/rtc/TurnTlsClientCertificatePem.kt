package jp.shiguredo.sora.sdk.channel.rtc

import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Base64

internal object TurnTlsClientCertificatePem {
    private const val PEM_LINE_LENGTH = 64

    fun toCertificatePem(certificate: X509Certificate): String =
        buildPem(
            type = "CERTIFICATE",
            der = certificate.encoded,
        )

    fun toCertificateChainPem(certificates: List<X509Certificate>): String =
        certificates.joinToString(separator = "") { certificate ->
            toCertificatePem(certificate)
        }

    fun toPrivateKeyPem(privateKey: PrivateKey): String =
        buildPem(
            type = "PRIVATE KEY",
            der = privateKey.encoded,
        )

    private fun buildPem(
        type: String,
        der: ByteArray,
    ): String {
        val base64 = Base64.getMimeEncoder(PEM_LINE_LENGTH, "\n".toByteArray()).encodeToString(der)
        return "-----BEGIN $type-----\n$base64\n-----END $type-----\n"
    }
}

/**
 * リフレクションで libwebrtc の `PeerConnection.IceServer.Builder.setTlsClientCertificate` を呼び出す。
 *
 * このメソッドは Shiguredo パッチ (`android_turn_tls_client_certificate.patch`) で追加された API であり、
 * 標準の libwebrtc 公開 API には存在しない。
 *
 * `certificatePem` には単一証明書の PEM と証明書チェーン（concatenated PEM）の両方を指定できる。
 * 内部では `SSLIdentity::CreateFromPEMChainStrings()` が使用される。
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
