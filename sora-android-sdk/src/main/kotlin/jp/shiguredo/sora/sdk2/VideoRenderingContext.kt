package jp.shiguredo.sora.sdk2

import org.webrtc.EglBase
import org.webrtc.GlRectDrawer
import org.webrtc.RendererCommon.GlDrawer
import org.webrtc.RendererCommon.RendererEvents

/**
 * 映像の描画に使うコンテキストです。
 *
 * @param eglBaseContext EGL コンテキスト ([org.webrtc.EglBase.Context])
 * @param configAttributes EGL の設定。 [org.webrtc.EglBase] で定義されています。
 * @param drawer OpenGL の描画オブジェクト ([org.webrtc.RendererCommon.GlDrawer])
 *
 * @property rendererEvents 映像レンダラーのイベントハンドラ ([org.webrtc.RendererCommon.RendererEvents])
 */
class VideoRenderingContext(eglBaseContext: EglBase.Context? = null,
                            configAttributes: IntArray? = null,
                            val rendererEvents: RendererEvents? = null,
                            drawer: GlDrawer? = null) {

    /**
     * EGL ユーティリティ ([org.webrtc.EglBase])
     */
    val eglBase: EglBase

    /**
     * EGL の設定。 [org.webrtc.EglBase] で定義されています。
     */
    val configAttributes: IntArray

    /**
     * OpenGL の描画オブジェクト ([org.webrtc.RendererCommon.GlDrawer]])
     */
    val drawer: GlDrawer

    init {
        if (configAttributes != null) {
            this.configAttributes = configAttributes
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