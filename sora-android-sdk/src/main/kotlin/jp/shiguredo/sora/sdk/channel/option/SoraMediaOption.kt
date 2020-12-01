package jp.shiguredo.sora.sdk.channel.option

import org.webrtc.*

/**
 * Sora への接続オプションを表すクラスです
 */
class SoraMediaOption {

    companion object {
        val TAG = SoraMediaOption::class.simpleName
    }

    internal var audioDownstreamEnabled = false
    internal var audioUpstreamEnabled   = false
    internal var videoDownstreamEnabled = false
    internal var videoUpstreamEnabled   = false
    internal var multistreamEnabled     = false
    internal var simulcastEnabled       = false
    internal var simulcastQuality: SimulcastOption.Quality = SimulcastOption.Quality.High

    var spotlight : Int        = 0

    /**
     * スポットライト機能のアクティブな配信数を指定します
     *
     * cf.
     * - Sora ドキュメントのスポットライト機能
     *   [](https://sora.shiguredo.jp/doc/SPOTLIGHT.html)
     */
    set(value) {
        if (0 < value) {
            multistreamEnabled = true
        }
        field = value
    }

    /**
     * スポットライトが有効か否かを返します
     */
    fun isSpotlight() = spotlight > 0

    /**
     * 利用する VideoEncoderFactory を指定します
     */
    var videoEncoderFactory: VideoEncoderFactory? = null

    /**
     * 利用する VideoDecoderFactory を指定します
     */
    var videoDecoderFactory: VideoDecoderFactory? = null

    internal var videoCapturer:          VideoCapturer? = null

    internal var videoDownstreamContext: EglBase.Context? = null
    internal var videoUpstreamContext:   EglBase.Context? = null

    var videoCodec = SoraVideoOption.Codec.VP9

    // videoBitRate が正しい綴りだが後方互換性を壊すほどではないので放置する
    var videoBitrate: Int? = null

    /**
     * 映像の視聴を有効にします
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
     * 映像の配信を有効にします
     *
     * cf.
     * - `org.webrtc.VideoCapturer`
     * - `org.webrtc.EglBase`
     * - `org.webrtc.EglBase.Context`
     *
     * @param capturer `VideoCapturer` インスタンス
     * @param eglContext Egl コンテキスト
     */
    fun enableVideoUpstream(capturer:        VideoCapturer,
                            eglContext:      EglBase.Context?) {
        videoUpstreamEnabled = true
        videoCapturer        = capturer
        videoUpstreamContext = eglContext
    }

    /**
     * サイマルキャストを有効にします
     */
    fun enableSimulcast(quality: SimulcastOption.Quality = SimulcastOption.Quality.High) {
        simulcastEnabled = true
        simulcastQuality = quality
    }

    /**
     * 音声のオプション設定を指定します
     */
    var audioOption: SoraAudioOption = SoraAudioOption()

    /**
     * 音声の視聴を有効にします
     */
    fun enableAudioDownstream() {
        audioDownstreamEnabled = true
    }

    /**
     * 音声の配信を有効にします
     */
    fun enableAudioUpstream() {
        audioUpstreamEnabled = true
    }

    var audioCodec = SoraAudioOption.Codec.OPUS

    // audioBitRate が正しい綴りだが後方互換性を壊すほどではないので放置する
    var audioBitrate: Int? = null

    /**
     * マルチストリームを有効にします
     *
     * cf.
     * - Sora ドキュメントのマルチストリーム
     *   [](https://sora.shiguredo.jp/doc/MULTISTREAM.html)
     */
    fun enableMultistream() {
        multistreamEnabled = true
    }

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
     * enableCpuOveruseDetection
     *
     * JavaScript API の "googCpuOveruseDetection" に相当する設定項目です。
     */
    var enableCpuOveruseDetection: Boolean = true

    /**
     * tcpCandidatePolicy
     *
     * TcpCandidatePolicy を設定します。
     */
    var tcpCandidatePolicy: PeerConnection.TcpCandidatePolicy =
            PeerConnection.TcpCandidatePolicy.ENABLED
}

