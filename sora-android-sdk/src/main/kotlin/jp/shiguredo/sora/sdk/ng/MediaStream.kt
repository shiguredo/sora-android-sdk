package jp.shiguredo.sora.sdk.ng

import org.webrtc.EglBase
import org.webrtc.GlRectDrawer
import org.webrtc.RendererCommon.GlDrawer
import org.webrtc.RendererCommon.RendererEvents

class MediaStream internal constructor(val mediaChannel: MediaChannel) {

    var nativeVideoTrack: MediaStreamTrack? = null
    var nativeAudioTrack: MediaStreamTrack? = null

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