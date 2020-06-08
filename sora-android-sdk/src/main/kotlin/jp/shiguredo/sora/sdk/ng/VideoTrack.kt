package jp.shiguredo.sora.sdk.ng

/*
class VideoTrack: MediaStreamTrack() {

    override var kind: Kind = Kind.VIDEO

    var nativeTrack: org.webrtc.VideoTrack? = null
        internal set

    override var isEnabled: Boolean
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

    override fun close() {
        nativeTrack?.dispose()
    }

}

 */