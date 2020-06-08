package jp.shiguredo.sora.sdk.ng

class MediaStream internal constructor(val mediaChannel: MediaChannel,
                                       var nativeStream: org.webrtc.MediaStream) {

    private var _videoTrack: VideoTrack? = null

    var videoTrack: VideoTrack? = null
    set(value) {
        if (_videoTrack != null) {
            nativeStream?.removeTrack(_videoTrack!!.nativeTrack)
        }
        _videoTrack = value
    }

    var audioTrack: AudioTrack? = null
        set(value) {}

    /*
    var isEnabled: Boolean
        get() {
            return if (videoTrack != null) {
                videoTrack!!.isEnabled
            } else if (audioTrack != null) {
                audioTrack!!.isEnabled
            } else {
                false
            }
        }
        set(value) {
            videoTrack?.isEnabled = value
            audioTrack?.isEnabled = value
        }
     */


    // TODO: video capturer

    private var _videoRenderer: VideoRenderer? = null

    val videoRenderer: VideoRenderer?
    get() = _videoRenderer

    fun setVideoRenderer(newRenderer: VideoRenderer,
                         videoRenderingContext: VideoRenderingContext) {
        removeVideoRenderer()
        if (mediaChannel.configuration.managesVideoRendererLifecycle &&
                newRenderer.shouldInitialization()) {
            newRenderer.initialize(videoRenderingContext)
        }
        _videoRenderer = newRenderer
    }

    fun removeVideoRenderer() {
        if (mediaChannel.configuration.managesVideoRendererLifecycle &&
                _videoRenderer != null && _videoRenderer!!.shouldRelease()) {
            _videoRenderer!!.release()
        }
        _videoRenderer = null
    }

    internal fun close() {
        removeVideoRenderer()

        videoTrack?.close()
        videoTrack = null

        audioTrack?.close()
        audioTrack = null

        nativeStream.dispose()
    }

}