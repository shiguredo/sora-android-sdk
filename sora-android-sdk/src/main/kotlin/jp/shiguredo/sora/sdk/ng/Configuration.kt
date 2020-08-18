package jp.shiguredo.sora.sdk.ng

import android.content.Context
import android.media.MediaRecorder
import jp.shiguredo.sora.sdk.camera.CameraCapturerFactory
import jp.shiguredo.sora.sdk.channel.option.SoraAudioOption
import jp.shiguredo.sora.sdk.channel.option.SoraMediaOption
import jp.shiguredo.sora.sdk.channel.option.SoraVideoOption
import jp.shiguredo.sora.sdk.channel.signaling.message.OpusParams
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.VideoCapturer
import org.webrtc.VideoDecoderFactory
import org.webrtc.VideoEncoderFactory
import org.webrtc.audio.AudioDeviceModule

class Configuration(var context: Context,
                    var url: String,
                    var channelId: String,
                    var role: Role) {

    companion object {
        private val TAG = Configuration::class.simpleName!!
        const val DEFAULT_TIMEOUT_SECONDS = 10L
    }

    var timeout: Long = DEFAULT_TIMEOUT_SECONDS

    var videoEnabled = true
    var videoCodec: VideoCodec = VideoCodec.VP9
    var videoBitRate: Int? = null

    var videoCapturer: VideoCapturer? = null
    var videoFrameSize = VideoFrameSize.VGA
    var videoFps: Int = 30

    /**
     * 映像キャプチャーと映像レンダラーで共有して使われる映像描画コンテキスト
     */
    var sharedVideoRenderingContext = VideoRenderingContext()

    /**
     * 映像キャプチャーで使われる映像描画コンテキスト
     * null をセットした場合、 sharedVideoRenderingContext が使われる
     * デフォルトは null
     */
    var videoCapturerVideoRenderingContext: VideoRenderingContext? = null

    internal val usingVideoCapturerVideoRenderingContext: VideoRenderingContext
        get() = videoCapturerVideoRenderingContext ?: sharedVideoRenderingContext

    /**
     * 利用する VideoEncoderFactory を指定します
     */
    var videoEncoderFactory: VideoEncoderFactory? = null

    /**
     * 利用する VideoDecoderFactory を指定します
     */
    var videoDecoderFactory: VideoDecoderFactory? = null

    // true のとき、 MediaChannel を close すると video renderer も自動的に release する
    // 接続中の renderer のみ対象とする
    var videoRendererLifecycleManagementEnabled: Boolean = true

    var audioEnabled = true
    var audioCodec: AudioCodec = AudioCodec.OPUS
    var audioBitRate: Int? = null

    var multistreamEnabled     = false
    var simulcastEnabled       = false
    var spotlightEnabled       = false

    var spotlight: Int? = null

    // gson.toJson で扱える型。 Object?
    var signalingMetadata: Any? = null
    var signalingNotifyMetadata: Any? = null

    /**
     * 端末組み込みの acoustic echo canceler を使うかどうかの設定
     *
     * cf. `org.webrtc.JavaAudioDeviceModule.Builder#setUseHardwareAcousticEchoCanceler()`
     */
    var usesHardwareAcousticEchoCanceler: Boolean = true

    /**
     * 端末組み込みの noise suppressor を使うかどうかの設定
     *
     * cf. `org.webrtc.JavaAudioDeviceModule.Builder#setUseHardwareNoiseSuppressor()`
     */
    var usesHardwareNoiseSuppressor: Boolean = true

    /**
     * 利用する AudioDeviceModule の設定
     *
     * このプロパティを設定した場合、以下の設定は無視されます。
     *
     * - [usesHardwareAcousticEchoCanceler]
     * - [usesHardwareNoiseSuppressor]
     *
     * @see org.webrtc.audio.AudioDeviceModule
     */
    var audioDeviceModule: AudioDeviceModule? = null

    /**
     * 入力音声のエコーキャンセル処理の有無の設定
     */
    var audioProcessingEchoCancellationEnabled: Boolean = true

    /**
     * 入力音声の自動ゲイン調整処理の有無の設定
     */
    var audioProcessingAutoGainControlEnabled: Boolean = true

    /**
     * 入力音声のハイパスフィルタ処理の有無の設定
     */
    var audioProcessingHighpassFilterEnabled: Boolean = true

    /**
     * 入力音声のノイズ抑制処理の有無の設定
     */
    var audioProcessingNoiseSuppressionEnabled: Boolean = true

    /**
     * 音声の `MediaConstraints` の設定
     *
     * このプロパティを設定した場合、以下の設定は無視されます。
     *
     * - [audioProcessingEchoCancellationEnabled]
     * - [audioProcessingAutoGainControlEnabled]
     * - [audioProcessingHighpassFilterEnabled]
     * - [audioProcessingNoiseSuppressionEnabled]
     */
    var audioMediaConstraints: MediaConstraints? = null

    /**
     * 音声ソースの指定
     *
     * AudioDeviceModule 生成時に利用されます。
     * デフォルト値は `android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION` です。
     */
    var audioSource: Int = MediaRecorder.AudioSource.VOICE_COMMUNICATION

    /**
     * 入力をステレオにするかどうかのフラグ
     *
     * AudioDeviceModule の生成時に利用されます。
     * デフォルト値はモノラルです。
     */
    var inputAudioSound: AudioSound = AudioSound.MONO

    /**
     * 出力をステレオにするかどうかのフラグ
     *
     * AudioDeviceModule の生成時に利用されます。
     * デフォルト値はモノラルです。
     */
    var outputAudioSound: AudioSound = AudioSound.MONO

    /**
     * opus_params
     */
    var opusParams: OpusParams? = null

    private var isInitialized: Boolean = false

    private fun initialize() {
        if (isInitialized)
            return

        SoraLogger.d(TAG, "initialize configuration")

        if (role.isSender) {
            SoraLogger.d(TAG, "create video capturer")
            videoCapturer = CameraCapturerFactory.create(context)
        }

        isInitialized = true
    }

    internal fun toSoraMediaOption(): SoraMediaOption {
        if (!isInitialized)
            initialize()

        return SoraMediaOption().also {
            it.multistreamEnabled = multistreamEnabled
            it.simulcastEnabled = simulcastEnabled

            if (spotlightEnabled && spotlight != null) {
                it.spotlight = spotlight!!
            }

            if (videoEnabled) {
                it.videoBitrate = videoBitRate

                it.videoCodec = when (videoCodec) {
                    VideoCodec.VP8 -> SoraVideoOption.Codec.VP8
                    VideoCodec.VP9 -> SoraVideoOption.Codec.VP9
                    VideoCodec.H264 -> SoraVideoOption.Codec.H264
                    else -> SoraVideoOption.Codec.VP9
                }

                it.videoEncoderFactory = videoEncoderFactory
                it.videoDecoderFactory = videoDecoderFactory

                SoraLogger.d(TAG, "role $role, ${role.isSender} ${role.isReceiver}")
                if (role.isSender) {
                    SoraLogger.d(TAG, "enable video upstream")
                    it.enableVideoUpstream(videoCapturer!!,
                            usingVideoCapturerVideoRenderingContext.eglBase.eglBaseContext)
                }

                if (role.isReceiver) {
                    it.enableVideoDownstream(sharedVideoRenderingContext.eglBase.eglBaseContext)
                }
            }

            if (audioEnabled) {
                it.audioUpstreamEnabled = role.isSender
                it.audioDownstreamEnabled = role.isReceiver

                it.audioBitrate = audioBitRate

                it.audioCodec = when (audioCodec) {
                    AudioCodec.OPUS -> SoraAudioOption.Codec.OPUS
                    AudioCodec.PCMU -> SoraAudioOption.Codec.PCMU
                }

                it.audioOption.audioDeviceModule = audioDeviceModule
                it.audioOption.audioSource = audioSource
                it.audioOption.useStereoInput = inputAudioSound == AudioSound.STEREO
                it.audioOption.useStereoOutput = outputAudioSound == AudioSound.STEREO
                it.audioOption.useHardwareAcousticEchoCanceler = usesHardwareAcousticEchoCanceler
                it.audioOption.useHardwareNoiseSuppressor = usesHardwareNoiseSuppressor
                it.audioOption.audioProcessingAutoGainControl = audioProcessingAutoGainControlEnabled
                it.audioOption.audioProcessingEchoCancellation = audioProcessingEchoCancellationEnabled
                it.audioOption.audioProcessingHighpassFilter = audioProcessingHighpassFilterEnabled
                it.audioOption.audioProcessingNoiseSuppression = audioProcessingNoiseSuppressionEnabled
                it.audioOption.mediaConstraints = audioMediaConstraints?.toNative()
                it.audioOption.opusParams = opusParams
            }
        }
    }

    fun printDebug(tag: String, message: String){
        SoraLogger.d(tag, """$message: Configuration:
            |url                     = $url
            |channelId               = $channelId
            |role                    = ${role.name}
            |multistreamEnabled      = $multistreamEnabled
            |simulcastEnabled        = $simulcastEnabled
            |spotlightEnabled        = $spotlightEnabled
            |spotlight               = $spotlight
            |videoEnabled            = $videoEnabled
            |videoCodec              = $videoCodec
            |videoBitRate            = $videoBitRate
            |videoCapturer           = $videoCapturer
            |videoFrameSize          = $videoFrameSize
            |videoFps                = $videoFps
            |videoEncoderFactory     = $videoEncoderFactory
            |videoDecoderFactory     = $videoDecoderFactory
            |audioCodec              = $audioCodec
            |audioBitRate            = $audioBitRate
            |signalingMetadata       = $signalingMetadata
            |signalingNotifyMetadata = ${this.signalingNotifyMetadata}""".trimMargin())
    }

}