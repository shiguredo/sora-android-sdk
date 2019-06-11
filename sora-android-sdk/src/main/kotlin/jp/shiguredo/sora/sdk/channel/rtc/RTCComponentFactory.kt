package jp.shiguredo.sora.sdk.channel.rtc

import android.content.Context
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule


class RTCComponentFactory(private val option: SoraMediaOption) {
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
            option.videoUpstreamContext != null ->
                DefaultVideoEncoderFactory(option.videoUpstreamContext,
                        true /* enableIntelVp8Encoder */,
                        false /* enableH264HighProfile */)
            else ->
                SoftwareVideoEncoderFactory()
        }

        val decoderFactory = when {
            option.videoDecoderFactory != null ->
                option.videoDecoderFactory!!
            option.videoDownstreamContext != null ->
                DefaultVideoDecoderFactory(option.videoDownstreamContext)
            else ->
                SoftwareVideoDecoderFactory()
        }

        decoderFactory.supportedCodecs.forEach {
            SoraLogger.d(TAG, "decoderFactory supported codec: ${it.name} ${it.params}")
        }
        encoderFactory.supportedCodecs.forEach {
            SoraLogger.d(TAG, "encoderFactory supported codec: ${it.name} ${it.params}")
        }
        val audioDeviceModule = createJavaAudioDevice(appContext)
        factoryBuilder
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
        audioDeviceModule.release()

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
                reportError(errorMessage)
            }

            override fun onWebRtcAudioRecordStartError(
                    errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode, errorMessage: String) {
                SoraLogger.e(TAG, "onWebRtcAudioRecordStartError: $errorCode. $errorMessage")
                reportError(errorMessage)
            }

            override fun onWebRtcAudioRecordError(errorMessage: String) {
                SoraLogger.e(TAG, "onWebRtcAudioRecordError: $errorMessage")
                reportError(errorMessage)
            }
        }

        val audioTrackErrorCallback = object : JavaAudioDeviceModule.AudioTrackErrorCallback {
            override fun onWebRtcAudioTrackInitError(errorMessage: String) {
                SoraLogger.e(TAG, "onWebRtcAudioTrackInitError: $errorMessage")
                reportError(errorMessage)
            }

            override fun onWebRtcAudioTrackStartError(
                    errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode, errorMessage: String) {
                SoraLogger.e(TAG, "onWebRtcAudioTrackStartError: $errorCode. $errorMessage")
                reportError(errorMessage)
            }

            override fun onWebRtcAudioTrackError(errorMessage: String) {
                SoraLogger.e(TAG, "onWebRtcAudioTrackError: $errorMessage")
                reportError(errorMessage)
            }
        }

        return JavaAudioDeviceModule.builder(appContext)
                // TODO(shino): 設定値を検討する
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                // TODO(shino): application までエラーを上げる
                .setAudioRecordErrorCallback(audioRecordErrorCallback)
                .setAudioTrackErrorCallback(audioTrackErrorCallback)
                .createAudioDeviceModule()
    }

    private fun reportError(errorMessage: String) {
        // TODO: Implement
    }

}
