package jp.shiguredo.sora.sdk.channel.option

import org.webrtc.EglBase
import org.webrtc.VideoCapturer

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

    fun enableAudioDownstream() {
        audioDownstreamEnabled = true
    }

    fun enableAudioUpstream() {
        audioUpstreamEnabled = true
    }

    fun enableMultistream() {
        multistreamEnabled = true
    }

    fun enableVideoDownstream(eglContext: EglBase.Context?) {
        videoDownstreamEnabled = true
        videoDownstreamContext = eglContext
    }

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
