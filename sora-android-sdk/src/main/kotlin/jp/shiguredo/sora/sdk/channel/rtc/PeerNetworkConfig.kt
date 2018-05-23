package jp.shiguredo.sora.sdk.channel.rtc

import jp.shiguredo.sora.sdk.channel.signaling.message.OfferConfig
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection

class PeerNetworkConfig(
        private val serverConfig: OfferConfig,
        private val enableTcp:    Boolean = false
) {
    fun createConfiguration(): PeerConnection.RTCConfiguration {

        val iceServers = gatherIceServerSetting(serverConfig)

        val conf = PeerConnection.RTCConfiguration(iceServers)

        if (serverConfig.iceTransportPolicy == "relay") {
            conf.iceTransportsType = PeerConnection.IceTransportsType.RELAY
        }

        conf.bundlePolicy             = PeerConnection.BundlePolicy.MAXBUNDLE
        conf.rtcpMuxPolicy            = PeerConnection.RtcpMuxPolicy.REQUIRE
        conf.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        conf.keyType                  = PeerConnection.KeyType.ECDSA

        conf.tcpCandidatePolicy = if (enableTcp)
            PeerConnection.TcpCandidatePolicy.ENABLED
        else
            PeerConnection.TcpCandidatePolicy.DISABLED

        conf.enableDtlsSrtp = true
        conf.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        return conf
    }

    private fun gatherIceServerSetting(
            serverConfig: OfferConfig): List<PeerConnection.IceServer> {
        val iceServers = mutableListOf<PeerConnection.IceServer>()
        serverConfig.iceServers.forEach {
            val server = it
            server.urls.forEach {
                val url = it
                iceServers.add(PeerConnection.IceServer.builder(url)
                        .setUsername(server.username)
                        .setPassword(server.credential)
                        .createIceServer())
            }
        }
        return iceServers
    }

}

