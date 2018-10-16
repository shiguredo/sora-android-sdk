package jp.shiguredo.sora.sdk.channel.rtc

import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*


class RTCComponentFactory(private val option: SoraMediaOption) {
    companion object {
        private val TAG = RTCComponentFactory::class.simpleName
    }

    // メインスレッド(UI スレッド)で呼ばれる必要がある。
    // そうでないと Effect の ClassLoader.loadClass で NPE が発生する。
    fun createPeerConnectionFactory(): PeerConnectionFactory {
        val cl = Thread.currentThread().contextClassLoader
        SoraLogger.d(TAG, "createPeerConnectionFactory(): classloader=${cl}")
        val options = PeerConnectionFactory.Options()
        val factoryBuilder = PeerConnectionFactory.builder()
                .setOptions(options)

        if (option.videoIsRequired) {
            val encoderFactory = option.videoUpstreamContext?.let {
                DefaultVideoEncoderFactory(option.videoUpstreamContext,
                        true /* enableIntelVp8Encoder */,
                        false /* enableH264HighProfile */)
            } ?: SoftwareVideoEncoderFactory()
            val decoderFactory = option.videoDownstreamContext?.let {
                DefaultVideoDecoderFactory(option.videoDownstreamContext)
            } ?: SoftwareVideoDecoderFactory()
            factoryBuilder.setVideoEncoderFactory(encoderFactory)
                    .setVideoDecoderFactory(decoderFactory)
        }

        return factoryBuilder.createPeerConnectionFactory()
    }

    fun createSDPConstraints(): MediaConstraints {
        val constraints = MediaConstraints()
        if (option.audioDownstreamEnabled) {
            constraints.mandatory.add(
                    MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        if (option.videoDownstreamEnabled) {
            constraints.mandatory.add(
                    MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        SoraLogger.d(TAG, "createSDPConstraints: ${constraints.toString()}")
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
