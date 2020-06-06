package jp.shiguredo.sora.sdk.ng

import org.webrtc.EglBase
import org.webrtc.GlRectDrawer
import org.webrtc.RendererCommon.GlDrawer
import org.webrtc.RendererCommon.RendererEvents

class MediaStream internal constructor(val mediaChannel: MediaChannel) {

    var videoTrack: MediaStreamTrack? = null
        internal set

    var audioTrack: MediaStreamTrack? = null
        internal set

    var isEnabled: Boolean = true
    set(value) {
        // TODO: トラック操作
    }

    // video capturer
    private var videoRenderer: VideoRenderer? = null

    fun setVideoRenderer(newRenderer: VideoRenderer?,
                         rendererEvents: RendererEvents? = null,
                         configAttributes: IntArray? = null,
                         drawer: GlDrawer? = null) {
        if (newRenderer == null) {
            videoRenderer = null
            return
        }

        newRenderer!!.init(mediaChannel.configuration.eglBase.eglBaseContext,
                rendererEvents, configAttributes, drawer)
    }

    internal fun close() {
    }

}