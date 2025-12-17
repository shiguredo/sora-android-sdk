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

    /**
     * SDK 内部で CameraVideoCapturer を生成・管理するためのオプションです.
     *
     * @param captureType 映像の取得元
     * @param width 映像の幅
     * @param height 映像の高さ
     * @param frameRate 映像のフレームレート
     * @param frontFacingFirst 複数のカメラがある場合にフロントカメラを優先するかどうか
     * @param initialVideoHardMute Sora 接続開始時に映像をハードミュートするかどうか
     */
    data class SoraCameraConfig(
        val captureType: SoraVideoOption.CaptureType = SoraVideoOption.CaptureType.DEVICE_CAMERA,
        var width: Int = 640,
        var height: Int = 480,
        var frameRate: Int = 30,
        val frontFacingFirst: Boolean = true,
        val initialVideoHardMute: Boolean = false,
    )

    internal var audioDownstreamEnabled = false
    internal var audioUpstreamEnabled = false
    internal var videoDownstreamEnabled = false
    internal var videoUpstreamEnabled = false
    internal var multistreamEnabled: Boolean? = null
    internal var spotlightOption: SoraSpotlightOption? = null
    internal var simulcastEnabled = false
    internal var simulcastRid: SoraVideoOption.SimulcastRid? = null
    internal var simulcastRequestRid: SoraVideoOption.SimulcastRequestRid? = null

    internal val spotlightEnabled: Boolean
        get() = spotlightOption != null

    internal val canVideoCapturerControllable: Boolean
        get() = soraCameraConfig != null

    /**
     * 利用する VideoEncoderFactory を指定します.
     */
    var videoEncoderFactory: VideoEncoderFactory? = null

    /**
     * 利用する VideoDecoderFactory を指定します.
     */
    var videoDecoderFactory: VideoDecoderFactory? = null

    /**
     * 映像エンコードにソフトウェアエンコーダーのみを使用します.
     *
     * - 既定値は false で、ハードウェアエンコーダーを優先し、必要時にソフトウェアへフォールバックします.
     * - `videoEncoderFactory` を明示的に設定している場合は、このフラグは無視されます.
     * - サイマルキャストが有効な場合も、ソフトウェアのみの構成でエンコードします.
     */
    var softwareVideoEncoderOnly: Boolean = false

    private var videoCapturer: VideoCapturer? = null
    internal var soraCameraConfig: SoraCameraConfig? = null

    internal var videoDownstreamContext: EglBase.Context? = null
    internal var videoUpstreamContext: EglBase.Context? = null

    /**
     * 映像コーデック.
     *
     * 未設定の場合 Sora Android SDK は DEFAULT を設定する.
     * DEFAULT は Sora のデフォルト値を利用する.
     */
    var videoCodec = SoraVideoOption.Codec.DEFAULT

    // videoBitRate が正しい綴りだが後方互換性を壊すほどではないので放置する

    /**
     * 映像ビットレート.
     */
    var videoBitrate: Int? = null

    /**
     * VP9 向け映像コーデックパラメーター.
     */
    var videoVp9Params: Any? = null

    /**
     * AV1 向け映像コーデックパラメーター.
     */
    var videoAv1Params: Any? = null

    /**
     * H.264 向け映像コーデックパラメーター.
     */
    var videoH264Params: Any? = null

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
     * @param cameraConfig カメラ設定
     */
    @JvmOverloads
    fun enableVideoUpstream(
        capturer: VideoCapturer,
        eglContext: EglBase.Context?,
        cameraConfig: SoraCameraConfig? = null,
    ) {
        videoUpstreamEnabled = true
        videoCapturer = capturer
        videoUpstreamContext = eglContext
        soraCameraConfig = cameraConfig
    }

    /**
     * 映像の配信を有効にします (カメラキャプチャを SDK 内部で生成します).
     *
     * @param eglContext Egl コンテキスト
     * @param cameraConfig カメラ設定オプション
     */
    fun enableVideoUpstream(
        eglContext: EglBase.Context?,
        cameraConfig: SoraCameraConfig,
    ) {
        videoUpstreamEnabled = true
        videoUpstreamContext = eglContext
        soraCameraConfig = cameraConfig
    }

    /**
     * サイマルキャスト機能を有効にします.
     *
     * @param rid デフォルトで受信する映像の種類
     */
    @JvmOverloads
    @Deprecated(
        message = "シグナリング接続時の simulcast_rid 指定は2027 年 12 月リリース予定の Sora にて廃止予定です。",
        replaceWith = ReplaceWith("enableSimulcast(requestRid: SoraVideoOption.SimulcastRequestRid? = null)"),
    )
    fun enableSimulcast(rid: SoraVideoOption.SimulcastRid? = null) {
        enableSimulcastInternal(simulcastRid = rid, simulcastRequestRid = null)
    }

    /**
     * サイマルキャスト機能を有効にします.
     *
     * @param requestRid デフォルトで受信する映像の種類
     */
    fun enableSimulcast(requestRid: SoraVideoOption.SimulcastRequestRid? = null) {
        enableSimulcastInternal(simulcastRid = null, simulcastRequestRid = requestRid)
    }

    // サイマルキャストを有効化し、指定された RID を設定する内部関数。
    private fun enableSimulcastInternal(
        simulcastRid: SoraVideoOption.SimulcastRid?,
        simulcastRequestRid: SoraVideoOption.SimulcastRequestRid?,
    ) {
        simulcastEnabled = true
        simulcastRid?.let { this.simulcastRid = it }
        simulcastRequestRid?.let { this.simulcastRequestRid = it }
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
    fun enableSpotlight(
        option: SoraSpotlightOption,
        enableSimulcast: Boolean = true,
    ) {
        spotlightOption = option
        if (enableSimulcast) {
            enableSimulcastInternal(simulcastRid = null, simulcastRequestRid = null)
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
     *
     * 未設定の場合 Sora Android SDK は DEFAULT を設定する.
     * DEFAULT は Sora のデフォルト値を利用する.
     */
    var audioCodec = SoraAudioOption.Codec.DEFAULT

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
    @Deprecated(
        message = "レガシーストリーム機能は 2025 年 6 月リリースの Sora にて廃止します。",
    )
    fun enableMultistream() {
        multistreamEnabled = true
    }

    /**
     * レガシーストリームを有効にします.
     */
    @Deprecated(
        message = "レガシーストリーム機能は 2025 年 6 月リリースの Sora にて廃止します。",
    )
    fun enableLegacyStream() {
        multistreamEnabled = false
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

    /**
     * Upstream と Downstream の設定から、必要なロールを決定します.
     */
    internal val requiredRole: SoraChannelRole
        get() =
            if (upstreamIsRequired && downstreamIsRequired) {
                SoraChannelRole.SENDRECV
            } else if (upstreamIsRequired) {
                SoraChannelRole.SENDONLY
            } else {
                SoraChannelRole.RECVONLY
            }

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
     * (リソースの逼迫により) 送信する映像の品質が維持できない場合の挙動.
     * 映像エンコーダーがCPUやネットワーク帯域の制限に直面した際の振る舞いを制御します.
     * null(未指定)の場合、WebRTC 側でデフォルトの挙動(BALANCED)が適用されます.
     */
    var degradationPreference: SoraVideoOption.DegradationPreference? = null

    /**
     * プロキシ.
     */
    var proxy: SoraProxyOption = SoraProxyOption()

    /**
     * Sora の音声ストリーミング機能利用時に指定する言語コード.
     */
    var audioStreamingLanguageCode: String? = null

    /**
     * シグナリング type: connect メッセージの video に含めるデータがすべてデフォルト値かどうか.
     */
    internal fun isDefaultVideoOption(): Boolean =
        videoCodec == SoraVideoOption.Codec.DEFAULT &&
            videoBitrate == null &&
            videoVp9Params == null &&
            videoAv1Params == null &&
            videoH264Params == null

    /**
     * シグナリング type: connect メッセージの audio に含めるデータがすべてデフォルト値かどうか.
     */
    internal fun isDefaultAudioOption(): Boolean =
        audioCodec == SoraAudioOption.Codec.DEFAULT &&
            audioBitrate == null &&
            audioOption.opusParams == null

    /**
     * ユーザー設定の VideoCapturer を取得します.
     */
    internal fun userSettingVideoCapturer(): VideoCapturer? = videoCapturer
}
