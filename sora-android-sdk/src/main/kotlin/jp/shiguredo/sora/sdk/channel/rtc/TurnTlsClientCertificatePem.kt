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
                    "libwebrtc does not support TURN-TLS client certificates. " +
                        "Apply android_turn_tls_client_certificate.patch to the Android build.",
                )
        method.invoke(builder, privateKeyPem, certificatePem)
    }
}
