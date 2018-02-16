package jp.shiguredo.sora.sdk.channel.rtc

import jp.shiguredo.sora.sdk.channel.signaling.message.OfferConfig
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection

class PeerNetworkConfig(
        private val serverConfig: OfferConfig,
        private val enableTcp:    Boolean = false
) {
    fun createConstraints(): MediaConstraints {
        val constraints = MediaConstraints()
        constraints.mandatory.add(
                MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        return constraints
    }

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

        return conf
    }

    private fun gatherIceServerSetting(
            serverConfig: OfferConfig): List<PeerConnection.IceServer> {
        val iceServers = mutableListOf<PeerConnection.IceServer>()
        serverConfig.iceServers.forEach {
            val server = it
            server.urls.forEach {
                val url = it
                iceServers.add(PeerConnection.IceServer(url, server.username, server.credential))
            }
        }
        return iceServers
    }

}

