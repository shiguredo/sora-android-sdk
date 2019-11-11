package jp.shiguredo.sora.sdk.channel.rtc

import android.content.Context
import jp.shiguredo.sora.sdk.channel.option.SoraAudioOption
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.option.SoraVideoOption
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule


class RTCComponentFactory(private val option: SoraMediaOption,
                          private val listener: PeerChannel.Listener?) {
    companion object {
        private val TAG = RTCComponentFactory::class.simpleName
    }

    // メインスレッド(UI スレッド)で呼ばれる必要がある。
    // そうでないと Effect の ClassLoader.loadClass で NPE が発生する。
    fun createPeerConnectionFactory(appContext: Context): PeerConnectionFactory {
        val cl = Thread.currentThread().contextClassLoader
        SoraLogger.d(TAG, "createPeerConnectionFactory(): classloader=${cl}")
        val options = PeerConnectionFactory.Options()
        val factoryBuilder = PeerConnectionFactory.builder()
                .setOptions(options)

        val encoderFactory = when {
            option.videoEncoderFactory != null ->
                option.videoEncoderFactory!!
            option.videoUpstreamContext != null -> {
                if (option.videoCodec == SoraVideoOption.Codec.H264) {
                    HardwareVideoEncoderFactory(option.videoUpstreamContext,
                            true /* enableIntelVp8Encoder */,
                            false /* enableH264HighProfile */)
                } else {
                    DefaultVideoEncoderFactory(option.videoUpstreamContext,
                            true /* enableIntelVp8Encoder */,
                            false /* enableH264HighProfile */)
                }
            }
            option.videoDownstreamContext != null -> {
                if (option.videoCodec == SoraVideoOption.Codec.H264) {
                    HardwareVideoEncoderFactory(option.videoDownstreamContext,
                            true /* enableIntelVp8Encoder */,
                            false /* enableH264HighProfile */)
                } else {
                    DefaultVideoEncoderFactory(option.videoDownstreamContext,
                            true /* enableIntelVp8Encoder */,
                            false /* enableH264HighProfile */)
                }
            }
            else ->
                SoftwareVideoEncoderFactory()
        }

        val decoderFactory = when {
            option.videoDecoderFactory != null ->
                option.videoDecoderFactory!!
            option.videoDownstreamContext != null -> {
                if (option.videoCodec == SoraVideoOption.Codec.H264) {
                    HardwareVideoDecoderFactory(option.videoDownstreamContext)
                } else {
                    DefaultVideoDecoderFactory(option.videoDownstreamContext)
                }
            }
            else ->
                SoftwareVideoDecoderFactory()
        }

        decoderFactory.supportedCodecs.forEach {
            SoraLogger.d(TAG, "decoderFactory supported codec: ${it.name} ${it.params}")
        }
        encoderFactory.supportedCodecs.forEach {
            SoraLogger.d(TAG, "encoderFactory supported codec: ${it.name} ${it.params}")
        }
        val audioDeviceModule = when {
            option.audioOption.audioDeviceModule != null ->
                option.audioOption.audioDeviceModule!!
            else ->
                createJavaAudioDevice(appContext)
        }
        factoryBuilder
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
        // option で渡ってきた場合の所有権はアプリケーションにある。
        // ここで生成した場合だけ解放する。
        if (option.audioOption.audioDeviceModule == null) {
            audioDeviceModule.release()
        }

        return factoryBuilder.createPeerConnectionFactory()
    }

    fun createSDPConstraints(): MediaConstraints {
        val constraints = MediaConstraints()
        SoraLogger.d(TAG, "createSDPConstraints: ${constraints}")
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

    private fun createJavaAudioDevice(appContext: Context): AudioDeviceModule {

        val audioRecordErrorCallback = object : JavaAudioDeviceModule.AudioRecordErrorCallback {
            override fun onWebRtcAudioRecordInitError(errorMessage: String) {
                SoraLogger.e(TAG, "onWebRtcAudioRecordInitError: $errorMessage")
                reportError(SoraErrorReason.AUDIO_RECORD_INIT_ERROR, errorMessage)
            }

            override fun onWebRtcAudioRecordStartError(
                    errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode, errorMessage: String) {
                SoraLogger.e(TAG, "onWebRtcAudioRecordStartError: $errorCode. $errorMessage")
                reportError(SoraErrorReason.AUDIO_RECORD_START_ERROR, "$errorMessage [$errorCode]")
            }

            override fun onWebRtcAudioRecordError(errorMessage: String) {
                SoraLogger.e(TAG, "onWebRtcAudioRecordError: $errorMessage")
                reportError(SoraErrorReason.AUDIO_RECORD_ERROR, errorMessage)
            }
        }

        val audioTrackErrorCallback = object : JavaAudioDeviceModule.AudioTrackErrorCallback {
            override fun onWebRtcAudioTrackInitError(errorMessage: String) {
                SoraLogger.e(TAG, "onWebRtcAudioTrackInitError: $errorMessage")
                reportError(SoraErrorReason.AUDIO_TRACK_INIT_ERROR, errorMessage)
            }

            override fun onWebRtcAudioTrackStartError(
                    errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode, errorMessage: String) {
                SoraLogger.e(TAG, "onWebRtcAudioTrackStartError: $errorCode. $errorMessage")
                reportError(SoraErrorReason.AUDIO_TRACK_START_ERROR, "$errorMessage [$errorCode]")
            }

            override fun onWebRtcAudioTrackError(errorMessage: String) {
                SoraLogger.e(TAG, "onWebRtcAudioTrackError: $errorMessage")
                reportError(SoraErrorReason.AUDIO_TRACK_ERROR, errorMessage)
            }
        }

        return JavaAudioDeviceModule.builder(appContext)
                .setUseHardwareAcousticEchoCanceler(
                        JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()
                                && option.audioOption.useHardwareAcousticEchoCanceler)
                .setUseHardwareNoiseSuppressor(
                        JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()
                                && option.audioOption.useHardwareNoiseSuppressor)
                .setAudioRecordErrorCallback(audioRecordErrorCallback)
                .setAudioTrackErrorCallback(audioTrackErrorCallback)
                .setAudioSource(option.audioOption.audioSource)
                .setUseStereoInput(option.audioOption.useStereoInput)
                .setUseStereoOutput(option.audioOption.useStereoOutput)
                .createAudioDeviceModule()
    }

    private fun reportError(errorReason: SoraErrorReason, errorMessage: String) {
        listener?.onError(errorReason, errorMessage)
    }
}
