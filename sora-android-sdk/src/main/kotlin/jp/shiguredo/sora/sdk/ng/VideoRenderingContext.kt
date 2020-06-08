package jp.shiguredo.sora.sdk.ng

import org.webrtc.EglBase
import org.webrtc.GlRectDrawer
import org.webrtc.RendererCommon.GlDrawer
import org.webrtc.RendererCommon.RendererEvents

class VideoRenderingContext(eglBaseContext: EglBase.Context? = null,
                            configAttributes: IntArray? = null,
                            val rendererEvents: RendererEvents? = null,
                            drawer: GlDrawer? = null) {

    val eglBase: EglBase
    val configAttributes: IntArray
    val drawer: GlDrawer

    init {
        if (configAttributes != null) {
            this.configAttributes = configAttributes!!
        } else {
            this.configAttributes = EglBase.CONFIG_PLAIN
        }

        eglBase = EglBase.create(eglBaseContext, this.configAttributes)

        if (drawer != null) {
            this.drawer = GlRectDrawer()
        } else {
            this.drawer = GlRectDrawer()
        }
    }

}