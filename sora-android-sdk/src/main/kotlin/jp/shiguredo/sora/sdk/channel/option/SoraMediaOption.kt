package jp.shiguredo.sora.sdk.channel.option

import org.webrtc.EglBase
import org.webrtc.VideoCapturer

/**
 * Sora への接続オプションを表すクラスです
 */
class SoraMediaOption {

    val TAG = SoraMediaOption::class.simpleName

    internal var audioDownstreamEnabled = false
    internal var audioUpstreamEnabled   = false
    internal var videoDownstreamEnabled = false
    internal var multistreamEnabled     = false

    internal var videoCapturer:          VideoCapturer? = null

    internal var videoDownstreamContext: EglBase.Context? = null
    internal var videoUpstreamContext:   EglBase.Context? = null

    internal var videoUpstreamSnapshotEnabled = false

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
     * - Sora ドキュメントのスナップショット機能
     *   [](https://sora.shiguredo.jp/doc/SNAPSHOT.html)
     *
     * @param capturer `VideoCapturer` インスタンス
     * @param eglContext Egl コンテキスト
     * @param snapshotEnabled スナップショットを用いるか否か
     */
    fun enableVideoUpstream(capturer:        VideoCapturer,
                            eglContext:      EglBase.Context?,
                            snapshotEnabled: Boolean = false) {
        videoCapturer                = capturer
        videoUpstreamContext         = eglContext
        videoUpstreamSnapshotEnabled = snapshotEnabled
    }

    // Just for internal usage
    internal val videoIsRequired: Boolean
    get() = videoDownstreamEnabled || (videoCapturer != null)

    internal val videoHwAccelerationIsRequired: Boolean
    get() = (videoUpstreamContext != null) || (videoDownstreamContext != null)

    internal val audioIsRequired: Boolean
    get() = audioDownstreamEnabled || audioUpstreamEnabled

    internal val downstreamIsRequired: Boolean
    get() = audioDownstreamEnabled || videoDownstreamEnabled

    internal val upstreamIsRequired: Boolean
    get() = audioUpstreamEnabled || (videoCapturer != null)

    internal val multistreamIsRequired: Boolean
    get() = if (downstreamIsRequired && upstreamIsRequired) {
            // 双方向通信の場合は multistream フラグを立てる
            true
        } else {
            multistreamEnabled
        }

    internal val requiredRole: SoraChannelRole
    get() = if (upstreamIsRequired) SoraChannelRole.UPSTREAM else SoraChannelRole.DOWNSTREAM

}
