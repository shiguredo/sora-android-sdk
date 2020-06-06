package jp.shiguredo.sora.sdk.ng

import android.content.Context
import android.graphics.Point
import android.media.MediaRecorder
import jp.shiguredo.sora.sdk.camera.CameraCapturerFactory
import jp.shiguredo.sora.sdk.channel.signaling.message.OpusParams
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.VideoCapturer
import org.webrtc.audio.AudioDeviceModule
import java.net.URL

enum class Role {
    SEND,
    RECV,
    SENDRECV
}

/**
 * 利用できる映像コーデックを示します
 */
enum class VideoCodec {
    /** H.264 */
    H264,
    /** VP8 */
    VP8,
    /** VP9 */
    VP9
}

/**
 * 映像のフレームサイズをまとめるクラスです
 */
enum class VideoFrameSize {
    QQVGA,
    UHD4096x2160
}

enum class VideoDirection {
    PORTRAIT,
    LANDSCAPE
}

// TODO: deprecated
class VideoFrameSize0 {

    // 反転するメソッドがあればいい？

    companion object {

        /** QQVGA 160x120 */
        val QQVGA = Point(160, 120)
        /** QCIF  176x144 */
        val QCIF  = Point(176, 144)
        /** HQVGA 240x160 */
        val HQVGA = Point(240, 160)
        /** QVGA  320x240 */
        val QVGA  = Point(320, 240)
        /** VGA   640x480 */
        val VGA   = Point(640, 480)
        /** HD    1280x720 */
        val HD    = Point(1280, 720)
        /** FHD   1920x1080 */
        val FHD   = Point(1920, 1080)
        /** Res3840x1920   3840x1920 */
        val Res3840x1920 = Point(3840, 1920)
        /** UHD3840x2160   3840x2160 */
        val UHD3840x2160 = Point(3840, 2160)
        /** UHD4096x2160   4096x2160 */
        val UHD4096x2160 = Point(4096, 2160)

    }

}

/**
 * 利用できる音声コーデックを示します。
 */
enum class AudioCodec {
    /** Opus */
    OPUS,
    /** PCMU */
    PCMU
}

enum class AudioSound {
    STEREO,
    MONO,
}

class AudioConstraint {

    companion object {
        const val ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation"
        const val AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl"
        const val HIGH_PASS_FILTER_CONSTRAINT  = "googHighpassFilter"
        const val NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression"
    }

}

class Configuration @JvmOverloads constructor(
        var context: Context,
        var url: URL,
        var channelId: String?,
        var role: Role

){

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 10L
    }

    var timeout: Long = DEFAULT_TIMEOUT_SECONDS

    var eglBase: EglBase? = null

    var videoEnabled = false
    var videoCodec: VideoCodec = VideoCodec.VP9
    var videoBitRate: Int? = null

    var videoCapturer: VideoCapturer? = null

    // TODO: この2つ分ける必要あるのか？
    // eglBase 一つでいいのでは？
    // もしくは Configuration に不要？
    // VideoView を自前で初期化すべきでは
    var videoSendEglBaseContext: EglBase.Context? = null
    var videoRecvEglBaseContext: EglBase.Context? = null

    // true のとき、 MediaChannel を close すると video renderer も自動的に release する
    var releasesVideoRendererWhenDone: Boolean = true

    var audioEnabled = false
    var audioCodec: AudioCodec = AudioCodec.OPUS
    var audioBitRate: Int? = null

    var multistreamEnabled     = false
    var simulcastEnabled       = false
    var spotlightEnabled       = false

    // gson.toJson で扱える型。 Object?
    var signalingMetadata: Object? = null
    var signalingNotifyMetadata: Object? = null

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
     * 利用する AudioDeviceModule を指定します
     *
     * null でない場合、 [useHardwareAcousticEchoCanceler] と [useHardwareNoiseSuppressor] の
     * 設定は無視されます。
     *
     * cf `org.webrtc.AudioDeviceModule`
     */
    var audioDeviceModule: AudioDeviceModule? = null

    /**
     * 入力音声のエコーキャンセル処理の有無の設定
     *
     * false に設定すると音声の `org.webrtc.MediaConstraints` に以下の設定を追加します。
     * - `googEchoCancellation` : false
     */
    var audioProcessingEchoCancellationEnabled: Boolean = true

    /**
     * 入力音声の自動ゲイン調整処理の有無の設定
     *
     * false に設定すると音声の `org.webrtc.MediaConstraints` に以下の設定を追加します。
     * - `googAutoGainControl` : false
     */
    var audioProcessingAutoGainControlEnabled: Boolean = true

    /**
     * 入力音声のハイパスフィルタ処理の有無の設定
     *
     * false に設定すると音声の `org.webrtc.MediaConstraints` に以下の設定を追加します。
     * - `googHighpassFilter` : false
     */
    var audioProcessingHighpassFilterEnabled: Boolean = true

    /**
     * 入力音声のノイズ抑制処理の有無の設定
     *
     * false に設定すると音声の `org.webrtc.MediaConstraints` に以下の設定を追加します。
     * - `googNoiseSuppression` : false
     */
    var audioProcessingNoiseSuppressionEnabled: Boolean = true

    /**
     * 音声の `org.webrtc.MediaConstraints` を設定します
     *
     * null でない場合、 [audioProcessingEchoCancellation], [audioProcessingAutoGainControl],
     * [audioProcessingHighpassFilter], [audioProcessingNoiseSuppression] の設定は無視されます。
     */
    var audioMediaConstraints: MediaConstraints? = null

    /**
     * 音声ソースの指定
     *
     * AudioDeviceModule 生成時に利用されます。
     * デフォルト値は `android.media.MediaRecorder.AudioSource.MIC です。
     */
    var audioSource: Int = MediaRecorder.AudioSource.MIC

    /**
     * 入力をステレオにするかどうかのフラグ
     *
     * AudioDeviceModule 生成時に利用されます。
     * デフォルト値は false (モノラル) です。
     */
    var inputAudioSound: AudioSound = AudioSound.MONO

    /**
     * 出力をステレオにするかどうかのフラグ
     *
     * AudioDeviceModule 生成時に利用されます。
     * デフォルト値は false (モノラル) です。
     */
    var outputAudioSound: AudioSound = AudioSound.MONO

    /**
     * opus_params
     */
    var opusParams: OpusParams? = null

    init {

    }

}