package jp.shiguredo.sora.sdk.channel.rtc

import android.content.Context
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.codec.SimulcastVideoEncoderFactoryWrapper
import jp.shiguredo.sora.sdk.codec.SoraDefaultVideoEncoderFactory
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule

class RTCComponentFactory(
    private val mediaOption: SoraMediaOption,
    private val listener: PeerChannel.Listener?
) {
    companion object {
        private val TAG = RTCComponentFactory::class.simpleName
    }

    // メインスレッド(UI スレッド)で呼ばれる必要がある。
    // そうでないと Effect の ClassLoader.loadClass で NPE が発生する。
    fun createPeerConnectionFactory(appContext: Context): PeerConnectionFactory {
        val cl = Thread.currentThread().contextClassLoader
        SoraLogger.d(TAG, "createPeerConnectionFactory(): classloader=$cl")
        val factoryOptions = PeerConnectionFactory.Options()
        val factoryBuilder = PeerConnectionFactory.builder()
            .setOptions(factoryOptions)

        // DefaultVideoEncoderFactory, DefaultVideoDecoderFactory は
        // EglBase.Context を与えるとハードウェアエンコーダーを使用する
        SoraLogger.d(TAG, "videoEncoderFactory => ${mediaOption.videoEncoderFactory}")
        SoraLogger.d(TAG, "videoUpstreamContext => ${mediaOption.videoUpstreamContext}")
        val encoderFactory = when {
            mediaOption.videoEncoderFactory != null ->
                mediaOption.videoEncoderFactory!!
            mediaOption.simulcastEnabled ->
                SimulcastVideoEncoderFactoryWrapper(
                    mediaOption.videoUpstreamContext,
                    resolutionAdjustment = mediaOption.hardwareVideoEncoderResolutionAdjustment,
                )
            mediaOption.videoUpstreamContext != null ->
                SoraDefaultVideoEncoderFactory(
                    mediaOption.videoUpstreamContext,
                    resolutionAdjustment = mediaOption.hardwareVideoEncoderResolutionAdjustment,
                )

            mediaOption.videoDownstreamContext != null ->
                SoraDefaultVideoEncoderFactory(
                    mediaOption.videoDownstreamContext,
                    resolutionAdjustment = mediaOption.hardwareVideoEncoderResolutionAdjustment,
                )
            else ->
                SoraDefaultVideoEncoderFactory(
                    null,
                    resolutionAdjustment = mediaOption.hardwareVideoEncoderResolutionAdjustment,
                )
        }

        SoraLogger.d(TAG, "videoDecoderFactory => ${mediaOption.videoDecoderFactory}")
        SoraLogger.d(TAG, "videoDownstreamContext => ${mediaOption.videoDownstreamContext}")
        val decoderFactory = when {
            mediaOption.videoDecoderFactory != null ->
                mediaOption.videoDecoderFactory!!
            mediaOption.videoDownstreamContext != null ->
                DefaultVideoDecoderFactory(mediaOption.videoDownstreamContext)
            else ->
                DefaultVideoDecoderFactory(null)
        }

        SoraLogger.d(TAG, "decoderFactory => $decoderFactory")
        SoraLogger.d(TAG, "encoderFactory => $encoderFactory")

        decoderFactory.supportedCodecs.forEach {
            SoraLogger.d(TAG, "decoderFactory supported codec: ${it.name} ${it.params}")
        }
        encoderFactory.supportedCodecs.forEach {
            SoraLogger.d(TAG, "encoderFactory supported codec: ${it.name} ${it.params}")
        }
        val audioDeviceModule = when {
            mediaOption.audioOption.audioDeviceModule != null ->
                mediaOption.audioOption.audioDeviceModule!!
            else ->
                createJavaAudioDevice(appContext)
        }
        factoryBuilder
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
        // option で渡ってきた場合の所有権はアプリケーションにある。
        // ここで生成した場合だけ解放する。
        if (mediaOption.audioOption.audioDeviceModule == null) {
            audioDeviceModule.release()
        }

        return factoryBuilder.createPeerConnectionFactory()
    }

    fun createSDPConstraints(): MediaConstraints {
        val constraints = MediaConstraints()
        SoraLogger.d(TAG, "createSDPConstraints: $constraints")
        return constraints
    }

    fun createVideoManager(): RTCLocalVideoManager? {
        return mediaOption.videoCapturer?.let {
            val videoManager = RTCLocalVideoManager(it)
            SoraLogger.d(TAG, "videoManager created: $videoManager")
            videoManager
        }
    }

    fun createAudioManager(): RTCLocalAudioManager {
        return RTCLocalAudioManager(mediaOption.audioUpstreamEnabled)
    }

    private fun createJavaAudioDevice(appContext: Context): AudioDeviceModule {

        val audioRecordErrorCallback = object : JavaAudioDeviceModule.AudioRecordErrorCallback {
            override fun onWebRtcAudioRecordInitError(errorMessage: String) {
                SoraLogger.e(TAG, "onWebRtcAudioRecordInitError: $errorMessage")
                reportError(SoraErrorReason.AUDIO_RECORD_INIT_ERROR, errorMessage)
            }

            override fun onWebRtcAudioRecordStartError(
                errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode,
                errorMessage: String
            ) {
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
                errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode,
                errorMessage: String
            ) {
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
                JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported() &&
                    mediaOption.audioOption.useHardwareAcousticEchoCanceler
            )
            .setUseHardwareNoiseSuppressor(
                JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported() &&
                    mediaOption.audioOption.useHardwareNoiseSuppressor
            )
            .setAudioRecordErrorCallback(audioRecordErrorCallback)
            .setAudioTrackErrorCallback(audioTrackErrorCallback)
            .setAudioSource(mediaOption.audioOption.audioSource)
            .setUseStereoInput(mediaOption.audioOption.useStereoInput)
            .setUseStereoOutput(mediaOption.audioOption.useStereoOutput)
            .createAudioDeviceModule()
    }

    private fun reportError(errorReason: SoraErrorReason, errorMessage: String) {
        listener?.onError(errorReason, errorMessage)
    }
}
