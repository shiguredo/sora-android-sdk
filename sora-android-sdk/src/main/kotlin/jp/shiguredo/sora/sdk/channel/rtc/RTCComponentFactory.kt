package jp.shiguredo.sora.sdk.channel.rtc

import android.content.Context
import jp.shiguredo.sora.sdk.camera.CameraCapturerFactory
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.codec.SimulcastVideoEncoderFactoryWrapper
import jp.shiguredo.sora.sdk.codec.SoraDefaultVideoEncoderFactory
import jp.shiguredo.sora.sdk.error.SoraErrorReason
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory
import org.webrtc.VideoEncoderFactory
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.security.cert.X509Certificate

class RTCComponentFactory(
    private val mediaOption: SoraMediaOption,
    private val simulcastEnabled: Boolean,
    private val insecure: Boolean,
    private val caCertificate: X509Certificate?,
    private val listener: PeerChannel.Listener?,
) {
    companion object {
        private val TAG = RTCComponentFactory::class.simpleName
    }

    internal enum class VideoEncoderFactoryType {
        // ユーザー指定の VideoEncoderFactory をそのまま利用する
        CUSTOM,

        // Simulcast + ソフトウェアエンコーダーのみ: SoraDefaultVideoEncoderFactory (softwareOnly=true)
        SIMULCAST_SOFTWARE,

        // Simulcast + ハードウェアエンコーダー: SimulcastVideoEncoderFactoryWrapper
        SIMULCAST,

        // 映像送信あり (simulcast 無効): SoraDefaultVideoEncoderFactory (upstreamContext)
        UPSTREAM,

        // 映像受信のみ: SoraDefaultVideoEncoderFactory (downstreamContext)
        DOWNSTREAM,

        // 映像の送受信なし (音声のみ): SoraDefaultVideoEncoderFactory (context=null)
        NULL,
    }

    // Controllable ADM（内部生成時のみ設定）
    private var controllableAudioDevice: AudioDeviceModuleWrapper? = null
    private var ownedAudioDeviceModule: AudioDeviceModule? = null

    // AudioDeviceModule を解放する
    // リソースリーク防止用に段階的に解放する
    fun releaseOwnedAudioDeviceModule() {
        try {
            controllableAudioDevice?.dispose()
        } catch (e: Exception) {
            SoraLogger.w(TAG, "dispose controllable ADM failed: ${e.message}")
        } finally {
            try {
                ownedAudioDeviceModule?.release()
            } catch (e: Exception) {
                SoraLogger.w(TAG, "release ADM failed: ${e.message}")
            } finally {
                ownedAudioDeviceModule = null
                controllableAudioDevice = null
            }
        }
    }

    // controllableAudioDevice インスタンスが生成されているか
    // カスタム ADM が設定されていない場合に生成される
    internal fun hasControllableAdm(): Boolean = controllableAudioDevice != null

    // ADM の録音一時停止を行う
    internal suspend fun pauseControllableAdm(): Boolean = controllableAudioDevice?.pauseRecording() ?: false

    // ADM の録音を再開する
    internal suspend fun resumeControllableAdm(): Boolean = controllableAudioDevice?.resumeRecording() ?: false

    // メインスレッド(UI スレッド)で呼ばれる必要がある。
    // そうでないと Effect の ClassLoader.loadClass で NPE が発生する。
    fun createPeerConnectionFactory(appContext: Context): PeerConnectionFactory {
        val cl = Thread.currentThread().contextClassLoader
        SoraLogger.d(TAG, "createPeerConnectionFactory(): classloader=$cl")
        val factoryOptions = PeerConnectionFactory.Options()
        val factoryBuilder =
            PeerConnectionFactory
                .builder()
                .setOptions(factoryOptions)

        // DefaultVideoEncoderFactory, DefaultVideoDecoderFactory は
        // EglBase.Context を与えるとハードウェアエンコーダーを使用する
        SoraLogger.d(TAG, "videoEncoderFactory => ${mediaOption.videoEncoderFactory}")
        SoraLogger.d(TAG, "videoUpstreamContext => ${mediaOption.videoUpstreamContext}")
        SoraLogger.d(TAG, "softwareVideoEncoderOnly => ${mediaOption.softwareVideoEncoderOnly}")
        val encoderFactory = createVideoEncoderFactory()

        SoraLogger.d(TAG, "videoDecoderFactory => ${mediaOption.videoDecoderFactory}")
        val decoderFactory =
            when {
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
        val audioDeviceModule: AudioDeviceModule =
            when {
                mediaOption.audioOption.audioDeviceModule != null ->
                    // アプリ側でカスタム ADM がセットされている場合
                    mediaOption.audioOption.audioDeviceModule!!
                else -> {
                    // デフォルトの JavaAudioDeviceModule を使用する場合
                    val adm = createJavaAudioDevice(appContext)
                    ownedAudioDeviceModule = adm
                    controllableAudioDevice = AudioDeviceModuleWrapper(adm)
                    adm
                }
            }
        factoryBuilder
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)

        return factoryBuilder.createPeerConnectionFactory()
    }

    internal fun createVideoEncoderFactory(): VideoEncoderFactory {
        val resolutionAdjustment = mediaOption.hardwareVideoEncoderResolutionAdjustment
        // MediaOption やサイマルキャストの利用設定から VideoEncoderFactoryType を判定して適用する
        return when (determineVideoEncoderFactoryType()) {
            VideoEncoderFactoryType.CUSTOM ->
                mediaOption.videoEncoderFactory!!

            VideoEncoderFactoryType.SIMULCAST_SOFTWARE ->
                // NOTE: Simulcast を利用するかつ SW オンリーの場合に SoraDefaultVideoEncoderFactory を使う理由
                //
                // softwareOnly の場合に SoraDefaultVideoEncoderFactory 内で SoftwareVideoEncoderFactory が使われる
                // SoftwareVideoEncoderFactory は JNI で BuiltinVideoEncoderFactory を生成し、
                // その Create() が SimulcastEncoderAdapter を返すため、SW でも自動的に
                // Simulcast をサポートできるため SimulcastVideoEncoderFactory を使う SimulcastVideoEncoderFactoryWrapper は不要。
                // SimulcastEncoderAdapter 自体は与えられた VideoEncoderFactory から複数エンコーダを
                // 生成してサイマルキャストを実現するため、SoftwareVideoEncoderFactory でも問題なく機能する。
                SoraDefaultVideoEncoderFactory(
                    mediaOption.videoUpstreamContext,
                    resolutionAdjustment = resolutionAdjustment,
                    softwareOnly = mediaOption.softwareVideoEncoderOnly,
                )

            VideoEncoderFactoryType.SIMULCAST ->
                SimulcastVideoEncoderFactoryWrapper(
                    mediaOption.videoUpstreamContext,
                    resolutionAdjustment = resolutionAdjustment,
                )

            VideoEncoderFactoryType.UPSTREAM ->
                SoraDefaultVideoEncoderFactory(
                    mediaOption.videoUpstreamContext,
                    resolutionAdjustment = resolutionAdjustment,
                    softwareOnly = mediaOption.softwareVideoEncoderOnly,
                )

            VideoEncoderFactoryType.DOWNSTREAM ->
                SoraDefaultVideoEncoderFactory(
                    mediaOption.videoDownstreamContext,
                    resolutionAdjustment = resolutionAdjustment,
                    softwareOnly = mediaOption.softwareVideoEncoderOnly,
                )

            VideoEncoderFactoryType.NULL ->
                SoraDefaultVideoEncoderFactory(
                    null,
                    resolutionAdjustment = resolutionAdjustment,
                    softwareOnly = mediaOption.softwareVideoEncoderOnly,
                )
        }
    }

    // ビデオエンコーダーファクトリーの種別を判定する。
    // 実際の VideoEncoderFactory 生成は createVideoEncoderFactory() 側で行うため、
    // この関数は WebRTC ネイティブコードに依存せず単体テストが可能。
    internal fun determineVideoEncoderFactoryType(): VideoEncoderFactoryType =
        when {
            // ユーザー指定の VideoEncoderFactory が最優先
            mediaOption.videoEncoderFactory != null ->
                VideoEncoderFactoryType.CUSTOM

            // サイマルキャスト有効 + SW エンコーダーのみ + 映像送信あり
            simulcastEnabled && mediaOption.softwareVideoEncoderOnly && mediaOption.videoUpstreamEnabled ->
                VideoEncoderFactoryType.SIMULCAST_SOFTWARE

            // サイマルキャスト有効 + 映像送信あり
            simulcastEnabled && mediaOption.videoUpstreamEnabled ->
                VideoEncoderFactoryType.SIMULCAST

            // 映像送信あり (サイマルキャスト無効)
            mediaOption.videoUpstreamContext != null ->
                VideoEncoderFactoryType.UPSTREAM

            // 映像受信のみ (サイマルキャスト無効 または videoUpstreamEnabled=false で fallback)
            mediaOption.videoDownstreamContext != null ->
                VideoEncoderFactoryType.DOWNSTREAM

            // 映像の送受信なし (音声のみ)
            else ->
                VideoEncoderFactoryType.NULL
        }

    internal fun createSSLCertificateVerifier() =
        TurnTlsCertificateVerifier(
            insecure = insecure,
            caCertificate = caCertificate,
        )

    fun createSDPConstraints(): MediaConstraints {
        val constraints = MediaConstraints()
        SoraLogger.d(TAG, "createSDPConstraints: $constraints")
        return constraints
    }

    fun createVideoManager(context: Context): RTCLocalVideoManager? {
        // ユーザー設定の VideoCapturer がある場合はそれを使う
        mediaOption.userSettingVideoCapturer()?.let {
            val videoManager = RTCLocalVideoManager(it, mediaOption.soraCameraConfig)
            SoraLogger.d(TAG, "videoManager created: $videoManager")
            return videoManager
        }

        // VideoCapturer が未設定かつ、soraCameraConfig が設定されている場合は
        // SDK 内部で VideoCapturer を生成する
        mediaOption.soraCameraConfig?.let { config ->
            val capturer = CameraCapturerFactory.create(context, config.frontFacingFirst)
            if (capturer == null) {
                SoraLogger.e(TAG, "create RTCLocalVideoManager failed")
                return null
            }

            // VideoCapturer を SDK 内部で生成しているため isOwnedCapturer=true を指定する
            val videoManager = RTCLocalVideoManager(capturer, config, true)
            SoraLogger.d(TAG, "videoManager created: $videoManager")
            return videoManager
        }

        return null
    }

    fun createAudioManager(): RTCLocalAudioManager = RTCLocalAudioManager(mediaOption.audioUpstreamEnabled)

    private fun createJavaAudioDevice(appContext: Context): JavaAudioDeviceModule {
        val audioRecordErrorCallback =
            object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(errorMessage: String) {
                    SoraLogger.e(TAG, "onWebRtcAudioRecordInitError: $errorMessage")
                    reportError(SoraErrorReason.AUDIO_RECORD_INIT_ERROR, errorMessage)
                }

                override fun onWebRtcAudioRecordStartError(
                    errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode,
                    errorMessage: String,
                ) {
                    SoraLogger.e(TAG, "onWebRtcAudioRecordStartError: $errorCode. $errorMessage")
                    reportError(SoraErrorReason.AUDIO_RECORD_START_ERROR, "$errorMessage [$errorCode]")
                }

                override fun onWebRtcAudioRecordError(errorMessage: String) {
                    SoraLogger.e(TAG, "onWebRtcAudioRecordError: $errorMessage")
                    reportError(SoraErrorReason.AUDIO_RECORD_ERROR, errorMessage)
                }
            }

        val audioTrackErrorCallback =
            object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                override fun onWebRtcAudioTrackInitError(errorMessage: String) {
                    SoraLogger.e(TAG, "onWebRtcAudioTrackInitError: $errorMessage")
                    reportError(SoraErrorReason.AUDIO_TRACK_INIT_ERROR, errorMessage)
                }

                override fun onWebRtcAudioTrackStartError(
                    errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode,
                    errorMessage: String,
                ) {
                    SoraLogger.e(TAG, "onWebRtcAudioTrackStartError: $errorCode. $errorMessage")
                    reportError(SoraErrorReason.AUDIO_TRACK_START_ERROR, "$errorMessage [$errorCode]")
                }

                override fun onWebRtcAudioTrackError(errorMessage: String) {
                    SoraLogger.e(TAG, "onWebRtcAudioTrackError: $errorMessage")
                    reportError(SoraErrorReason.AUDIO_TRACK_ERROR, errorMessage)
                }
            }

        return JavaAudioDeviceModule
            .builder(appContext)
            .setUseHardwareAcousticEchoCanceler(
                JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported() &&
                    mediaOption.audioOption.useHardwareAcousticEchoCanceler,
            ).setUseHardwareNoiseSuppressor(
                JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported() &&
                    mediaOption.audioOption.useHardwareNoiseSuppressor,
            ).setAudioRecordErrorCallback(audioRecordErrorCallback)
            .setAudioTrackErrorCallback(audioTrackErrorCallback)
            .setAudioSource(mediaOption.audioOption.audioSource)
            .setUseStereoInput(mediaOption.audioOption.useStereoInput)
            .setUseStereoOutput(mediaOption.audioOption.useStereoOutput)
            .createAudioDeviceModule()
    }

    private fun reportError(
        errorReason: SoraErrorReason,
        errorMessage: String,
    ) {
        listener?.onError(errorReason, errorMessage)
    }
}
