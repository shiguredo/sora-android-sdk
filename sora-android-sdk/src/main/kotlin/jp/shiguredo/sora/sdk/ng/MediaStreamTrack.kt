package jp.shiguredo.sora.sdk.ng

import org.webrtc.MediaStreamTrack
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver

class MediaStreamTrack internal constructor(var kind: Kind) {

    enum class Kind {
        VIDEO,
        AUDIO
    }

    enum class State {
        LIVE,
        ENDED
    }

    var state: State = State.LIVE
        internal set

    var stream: MediaStream? = null
        internal set

    var isEnabled: Boolean
        get() {
            return if (nativeTrack != null) {
                nativeTrack!!.enabled()
            } else {
                true
            }
        }
        set(value) {
            nativeTrack?.setEnabled(value)
        }

    var nativeTrack: org.webrtc.MediaStreamTrack? = null
        internal set

    var nativeSender: RtpSender? = null
        internal set

    var nativeReceiver: RtpReceiver? = null
        internal set

    var nativeTransiever: RtpTransceiver? = null
        internal set

}