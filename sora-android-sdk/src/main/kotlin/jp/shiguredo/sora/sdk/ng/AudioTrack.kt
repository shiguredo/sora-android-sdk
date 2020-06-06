package jp.shiguredo.sora.sdk.ng

class AudioTrack: MediaStreamTrack() {

    override var kind: Kind = Kind.AUDIO

    var nativeTrack: org.webrtc.AudioTrack? = null
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

    fun setVolume(value: Double) {
        nativeTrack?.setVolume(value)
    }

    override fun close() {
        // 特に何もする必要なし
    }

}
