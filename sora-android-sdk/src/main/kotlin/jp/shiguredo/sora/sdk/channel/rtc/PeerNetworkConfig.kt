package jp.shiguredo.sora.sdk.channel.rtc

import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.signaling.message.OfferConfig
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.CryptoOptions
import org.webrtc.PeerConnection

class PeerNetworkConfig(
    private val serverConfig: OfferConfig?,
    private val mediaOption: SoraMediaOption,
    private val insecure: Boolean = false,
) {
    companion object {
        private val TAG = PeerNetworkConfig::class.simpleName
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

    private fun gatherIceServerSetting(serverConfig: OfferConfig?): List<PeerConnection.IceServer> {
        val iceServers = mutableListOf<PeerConnection.IceServer>()
        serverConfig?.let {
            it.iceServers.forEach {
                val server = it
                server.urls.forEach {
                    val url = it
                    iceServers.add(
                        PeerConnection.IceServer
                            .builder(url)
                            .setUsername(server.username)
                            .setPassword(server.credential)
                            .apply {
                                if (insecure) {
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
