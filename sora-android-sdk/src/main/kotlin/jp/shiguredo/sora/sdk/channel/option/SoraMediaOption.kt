package jp.shiguredo.sora.sdk.channel.option

import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.VideoCapturer

/**
 * Sora への接続オプションを表すクラスです
 */
class SoraMediaOption {

    val TAG = SoraMediaOption::class.simpleName

    internal var audioDownstreamEnabled = false
    internal var audioUpstreamEnabled   = false
    internal var videoDownstreamEnabled = false
    internal var videoUpstreamEnabled = false
    internal var multistreamEnabled     = false

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

    internal var videoCapturer:          VideoCapturer? = null

    internal var videoDownstreamContext: EglBase.Context? = null
    internal var videoUpstreamContext:   EglBase.Context? = null

    var videoCodec = SoraVideoOption.Codec.VP9
    var audioCodec = SoraAudioOption.Codec.OPUS

    var videoBitrate: Int? = null

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

    internal val multistreamIsRequired: Boolean
    get() = if (downstreamIsRequired && upstreamIsRequired) {
            // 双方向通信の場合は multistream フラグを立てる
            true
        } else {
            multistreamEnabled
        }

    internal val requiredRole: SoraChannelRole
    get() = if (upstreamIsRequired) SoraChannelRole.UPSTREAM else SoraChannelRole.DOWNSTREAM

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

    /**
     * SDP semantics
     *
     * Unified Plan のみ動作確認しています
     */
    var sdpSemantics: PeerConnection.SdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

    fun planB(): Boolean {
        return sdpSemantics == PeerConnection.SdpSemantics.PLAN_B
    }

}
