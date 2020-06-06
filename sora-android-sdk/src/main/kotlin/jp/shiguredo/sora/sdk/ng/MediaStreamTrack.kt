package jp.shiguredo.sora.sdk.ng

import org.webrtc.MediaStreamTrack
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver

abstract class MediaStreamTrack {

    enum class Kind {
        VIDEO,
        AUDIO
    }

    enum class State {
        LIVE,
        ENDED
    }

    abstract var kind: Kind

    var state: State = State.LIVE
        internal set

    var stream: MediaStream? = null
        internal set

    abstract var isEnabled: Boolean

    var nativeSender: RtpSender? = null
        internal set

    var nativeReceiver: RtpReceiver? = null
        internal set

    var nativeTransiever: RtpTransceiver? = null
        internal set

    internal abstract fun close()

}