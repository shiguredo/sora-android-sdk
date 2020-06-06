package jp.shiguredo.sora.sdk.ng

import org.webrtc.EglBase
import org.webrtc.GlRectDrawer
import org.webrtc.MediaStream
import org.webrtc.RendererCommon.GlDrawer
import org.webrtc.RendererCommon.RendererEvents

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
    private var videoRenderer: VideoRenderer? = null

    // TODO: renderer の release のタイミングはどうなる？
    fun setVideoRenderer(newRenderer: VideoRenderer,
                         renderingContext: RenderingContext) {
        if (videoRenderer != null) {

        }
        newRenderer.init(renderingContext)
        videoRenderer = newRenderer
    }

    internal fun close() {
        videoTrack?.close()
        videoTrack = null

        audioTrack?.close()
        audioTrack = null

        nativeStream.dispose()
    }

}