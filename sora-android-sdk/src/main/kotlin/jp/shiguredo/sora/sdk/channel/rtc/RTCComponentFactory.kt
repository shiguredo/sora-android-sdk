package jp.shiguredo.sora.sdk.channel.rtc

import android.content.Context
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory

class RTCComponentFactory(val option: SoraMediaOption) {

    // This method must be call in rtc-thread
    fun createPeerConnectionFactory(appContext: Context): PeerConnectionFactory {

        PeerConnectionFactory.initializeFieldTrials("")

        val options = PeerConnectionFactory.Options()
        val factory = PeerConnectionFactory(options)

        if (option.videoIsRequired) {
            factory.setVideoHwAccelerationOptions(option.videoUpstreamContext,
                    option.videoDownstreamContext)
        }

        return factory
    }

    fun createSDPConstraints(): MediaConstraints {
        val constraints = MediaConstraints()
        if (option.audioDownstreamEnabled) {
            constraints.mandatory.add(
                    MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        } else {
            constraints.mandatory.add(
                    MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }
        if (option.videoDownstreamEnabled) {
            constraints.mandatory.add(
                    MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        } else {
            constraints.mandatory.add(
                    MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        return constraints
    }

    fun createVideoManager() : RTCLocalVideoManager {
        val videoManager = option.videoCapturer?.let {
            RTCLocalVideoManagerImpl(it)
        } ?: RTCNullLocalVideoManager()
        return videoManager
    }

    fun createAudioManager(): RTCLocalAudioManager {
        return RTCLocalAudioManager(option.audioUpstreamEnabled)
    }
}
