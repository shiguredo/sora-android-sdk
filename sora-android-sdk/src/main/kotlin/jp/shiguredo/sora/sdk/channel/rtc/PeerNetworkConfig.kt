package jp.shiguredo.sora.sdk.channel.rtc

import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.signaling.message.OfferConfig
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.CryptoOptions
import org.webrtc.PeerConnection
import java.security.PrivateKey
import java.security.cert.X509Certificate

class PeerNetworkConfig(
    private val serverConfig: OfferConfig?,
    private val mediaOption: SoraMediaOption,
    private val insecure: Boolean = false,
    private val clientCertificate: X509Certificate? = null,
    private val clientPrivateKey: PrivateKey? = null,
    private val clientCertificateChain: List<X509Certificate>? = null,
) {
    companion object {
        private val TAG = PeerNetworkConfig::class.simpleName
    }

    init {
        // SoraMediaChannel 以外から直接生成される経路でも不正な証明書設定を早期に検出する。
        // TURN-TLS のクライアント証明書設定は clientCertificate / clientCertificateChain が排他であり、
        // いずれかを指定する場合は対応する clientPrivateKey も必須である。
        require(clientCertificate == null || clientCertificateChain == null) {
            "clientCertificate and clientCertificateChain are mutually exclusive"
        }
        require(clientCertificateChain == null || clientCertificateChain.isNotEmpty()) {
            "clientCertificateChain must not be empty"
        }
        require((clientCertificate != null || clientCertificateChain != null) == (clientPrivateKey != null)) {
            "either clientCertificate or clientCertificateChain and clientPrivateKey must be specified together"
        }
    }

    fun createConfiguration(): PeerConnection.RTCConfiguration {
        val iceServers = gatherIceServerSetting(serverConfig)

        val conf = PeerConnection.RTCConfiguration(iceServers)

        if (serverConfig?.iceTransportPolicy == "relay") {
            conf.iceTransportsType = PeerConnection.IceTransportsType.RELAY
        }

        conf.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        conf.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        conf.keyType = PeerConnection.KeyType.ECDSA
        val cryptoOptions =
            CryptoOptions
                .builder()
                .setEnableGcmCryptoSuites(true)
                .createCryptoOptions()
        conf.cryptoOptions = cryptoOptions
        conf.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        conf.tcpCandidatePolicy = mediaOption.tcpCandidatePolicy
        conf.enableCpuOveruseDetection = mediaOption.enableCpuOveruseDetection

        return conf
    }

    /**
     * シグナリングから受け取った ICE サーバー設定を libwebrtc の IceServer リストに変換する。
     *
     * turns: URL かつクライアント秘密鍵が指定されている場合、リフレクション経由で
     * `setTlsClientCertificate` を呼び出し TURN-TLS のクライアント認証を設定する。
     * `certificatePem` には単一証明書と証明書チェーン（concatenated PEM）の両方に対応する。
     *
     * insecure が true かつ turns: URL の場合は TLS 証明書検証をスキップする。
     */
    private fun gatherIceServerSetting(serverConfig: OfferConfig?): List<PeerConnection.IceServer> {
        val iceServers = mutableListOf<PeerConnection.IceServer>()
        serverConfig?.let {
            it.iceServers.forEach { server ->
                server.urls.forEach { url ->
                    iceServers.add(
                        PeerConnection.IceServer
                            .builder(url)
                            .setUsername(server.username)
                            .setPassword(server.credential)
                            .apply {
                                if (url.startsWith("turns:") && clientPrivateKey != null) {
                                    val certificatePem =
                                        when {
                                            clientCertificate != null ->
                                                TurnTlsClientCertificatePem.toCertificatePem(clientCertificate)
                                            clientCertificateChain != null ->
                                                TurnTlsClientCertificatePem.toCertificateChainPem(clientCertificateChain)
                                            else -> null
                                        }
                                    if (certificatePem != null) {
                                        TurnTlsClientCertificateConfigurer.applyToIceServerBuilder(
                                            builder = this,
                                            privateKeyPem = TurnTlsClientCertificatePem.toPrivateKeyPem(clientPrivateKey),
                                            certificatePem = certificatePem,
                                        )
                                    }
                                }
                                if (insecure && url.startsWith("turns:")) {
                                    SoraLogger.w(TAG, "[rtc] insecure is enabled for TURN-TLS: $url")
                                    setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
                                }
                            }.createIceServer(),
                    )
                }
            }
        }
        return iceServers
    }
}
