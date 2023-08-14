package jp.shiguredo.sora.sdk.channel.option

import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.VideoCapturer
import org.webrtc.VideoDecoderFactory
import org.webrtc.VideoEncoderFactory

/**
 * Sora への接続オプションを表すクラスです.
 */
class SoraMediaOption {

    companion object {
        val TAG = SoraMediaOption::class.simpleName
    }

    internal var audioDownstreamEnabled = false
    internal var audioUpstreamEnabled = false
    internal var videoDownstreamEnabled = false
    internal var videoUpstreamEnabled = false
    internal var multistreamEnabled = false
    internal var spotlightOption: SoraSpotlightOption? = null
    internal var simulcastEnabled = false
    internal var simulcastRid: SoraVideoOption.SimulcastRid? = null

    internal val spotlightEnabled: Boolean
        get() = spotlightOption != null

    /**
     * 利用する VideoEncoderFactory を指定します.
     */
    var videoEncoderFactory: VideoEncoderFactory? = null

    /**
     * 利用する VideoDecoderFactory を指定します.
     */
    var videoDecoderFactory: VideoDecoderFactory? = null

    internal var videoCapturer: VideoCapturer? = null

    internal var videoDownstreamContext: EglBase.Context? = null
    internal var videoUpstreamContext: EglBase.Context? = null

    /**
     * 映像コーデック.
     *
     * 未設定の場合、 Sora Android SDK は VP9 を設定します.
     */
    var videoCodec = SoraVideoOption.Codec.VP9

    /**
     * 映像ビットレート.
     */
    // videoBitRate が正しい綴りだが後方互換性を壊すほどではないので放置する
    var videoBitrate: Int? = null

    /**
     * 映像の視聴を有効にします.
     *
     * cf.
     * - `org.webrtc.EglBase`
     * - `org.webrtc.EglBase.Context`
     *
     * @param eglContext Egl コンテキスト
     */
    fun enableVideoDownstream(eglContext: EglBase.Context?) {
        videoDownstreamEnabled = true
        videoDownstreamContext = eglContext
    }

    /**
     * 映像の配信を有効にします.
     *
     * cf.
     * - `org.webrtc.VideoCapturer`
     * - `org.webrtc.EglBase`
     * - `org.webrtc.EglBase.Context`
     *
     * @param capturer `VideoCapturer` インスタンス
     * @param eglContext Egl コンテキスト
     */
    fun enableVideoUpstream(
        capturer: VideoCapturer,
        eglContext: EglBase.Context?
    ) {
        videoUpstreamEnabled = true
        videoCapturer = capturer
        videoUpstreamContext = eglContext
    }

    /**
     * サイマルキャスト機能を有効にします.
     *
     * @param rid デフォルトで受信する映像の種類
     */
    @JvmOverloads
    fun enableSimulcast(rid: SoraVideoOption.SimulcastRid? = null) {
        simulcastEnabled = true
        simulcastRid = rid
    }

    /**
     * スポットライト機能を有効にします.
     *
     * スポットライト機能では、複数の映像を送信するためにサイマルキャスト機能を利用しています.
     * この関数を実行することで、マルチストリーム機能が有効になります.
     * 第2引数の enableSimulcast に false を指定しない場合は、サイマルキャスト機能も有効になります.
     *
     * @param option スポットライト機能のオプション
     * @param enableSimulcast サイマルキャスト機能の利用の有無
     */
    @JvmOverloads
    fun enableSpotlight(option: SoraSpotlightOption, enableSimulcast: Boolean = true) {
        spotlightOption = option
        multistreamEnabled = true

        if (enableSimulcast) {
            enableSimulcast()
        }
    }

    /**
     * 音声のオプション設定を指定します.
     */
    var audioOption: SoraAudioOption = SoraAudioOption()

    /**
     * 音声の視聴を有効にします.
     */
    fun enableAudioDownstream() {
        audioDownstreamEnabled = true
    }

    /**
     * 音声の配信を有効にします.
     */
    fun enableAudioUpstream() {
        audioUpstreamEnabled = true
    }

    /**
     * 音声コーデック.
     */
    var audioCodec = SoraAudioOption.Codec.OPUS

    // audioBitRate が正しい綴りだが後方互換性を壊すほどではないので放置する
    /**
     * 音声ビットレート.
     */
    var audioBitrate: Int? = null

    /**
     * マルチストリームを有効にします.
     *
     * cf.
     * - Sora ドキュメントのマルチストリーム
     *   [](https://sora.shiguredo.jp/doc/MULTISTREAM.html)
     */
    fun enableMultistream() {
        multistreamEnabled = true
    }

    /**
     * ロール.
     *
     * 未設定の場合、 Sora Android SDK はロールを自動的に決定します
     */
    var role: SoraChannelRole? = null

    // Just for internal usage
    internal val videoIsRequired: Boolean
        get() = videoDownstreamEnabled || videoUpstreamEnabled

    internal val videoHwAccelerationIsRequired: Boolean
        get() = (videoUpstreamContext != null) || (videoDownstreamContext != null)

    internal val audioIsRequired: Boolean
        get() = audioDownstreamEnabled || audioUpstreamEnabled

    internal val downstreamIsRequired: Boolean
        get() = audioDownstreamEnabled || videoDownstreamEnabled

    internal val upstreamIsRequired: Boolean
        get() = audioUpstreamEnabled || videoUpstreamEnabled

    internal var _multistreamIsRequired: Boolean? = null

    internal var multistreamIsRequired: Boolean
        get() = when {
            _multistreamIsRequired != null ->
                _multistreamIsRequired!!
            downstreamIsRequired && upstreamIsRequired ->
                // 双方向通信の場合は multistream フラグを立てる
                true
            else ->
                multistreamEnabled
        }
        set(value) {
            _multistreamIsRequired = value
        }

    internal var _requiredRole: SoraChannelRole? = null

    internal val requiredRole: SoraChannelRole
        get() = if (upstreamIsRequired && downstreamIsRequired)
            SoraChannelRole.SENDRECV
        else if (upstreamIsRequired)
            SoraChannelRole.SENDONLY
        else
            SoraChannelRole.RECVONLY

    /**
     * JavaScript API の "googCpuOveruseDetection" に相当する設定項目です.
     */
    var enableCpuOveruseDetection: Boolean = true

    /**
     * TcpCandidatePolicy を設定します.
     */
    var tcpCandidatePolicy: PeerConnection.TcpCandidatePolicy =
        PeerConnection.TcpCandidatePolicy.ENABLED

    /**
     * HW エンコーダー利用時の解像度を調整するパラメータ.
     * HW エンコーダーに入力されるフレームの解像度が指定された数の倍数になるように調節します.
     *
     * このオプションを実装した経緯は以下の通りです.
     * - 解像度が 16 の倍数でない場合、 HW エンコーダーの初期化がエラーになる変更が libwebrtc のメインストリームに入った
     *   - Android CTS では、 HW エンコーダー (= MediaCodec) を 16 で割り切れる解像度のみでテストしており、かつ 16 で割り切れない解像度で問題が発生する端末があった
     * - Sora Android SDK では一部の解像度が影響を受けるため、対応としてこのオプションを実装した
     *
     * 参照: https://source.chromium.org/chromium/chromium/src/+/main:third_party/webrtc/sdk/android/src/java/org/webrtc/HardwareVideoEncoder.java;l=214-218;drc=0f50cc284949f225f663408e7d467f39d549d3dc
     *
     * Sora Android SDK では libwebrtc にパッチを当て、上記の HW エンコーダー初期化時の解像度のチェックを無効化しています.
     * そのため、このフラグを NONE に設定することで、従来通り、解像度を調整することなく HW エンコーダーを利用することも可能です.
     *
     * より詳細な情報を確認したい場合は、以下の Chromium のイシューを参照してください.
     *
     * https://bugs.chromium.org/p/chromium/issues/detail?id=1084702
     */
    var hardwareVideoEncoderResolutionAdjustment = SoraVideoOption.ResolutionAdjustment.MULTIPLE_OF_16

    /**
     * プロキシ.
     */
    var proxy: SoraProxyOption = SoraProxyOption()

    /**
     * Sora の音声ストリーミング機能利用時に指定する言語コード.
     */
    var audioStreamingLanguageCode: String? = null
}
