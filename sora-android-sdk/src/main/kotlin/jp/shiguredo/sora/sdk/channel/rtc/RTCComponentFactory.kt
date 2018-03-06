package jp.shiguredo.sora.sdk.channel.rtc

import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory

class RTCComponentFactory(private val option: SoraMediaOption) {
    val TAG = RTCComponentFactory::class.simpleName

    // メインスレッド(UI スレッド)で呼ばれる必要がある。
    // そうでないと Effect の ClassLoader.loadClass で NPE が発生する。
    fun createPeerConnectionFactory(): PeerConnectionFactory {
        SoraLogger.d(TAG, "createPeerConnectionFactory(): classloader=${Thread.currentThread().contextClassLoader}")
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
        SoraLogger.d(TAG, "videoManager created: ${videoManager}")
        return videoManager
    }

    fun createAudioManager(): RTCLocalAudioManager {
        return RTCLocalAudioManager(option.audioUpstreamEnabled)
    }
}
