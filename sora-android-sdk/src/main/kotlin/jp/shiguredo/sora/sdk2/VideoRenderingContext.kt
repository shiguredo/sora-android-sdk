package jp.shiguredo.sora.sdk2

import android.util.Log
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.EglBase
import org.webrtc.GlRectDrawer
import org.webrtc.RendererCommon.GlDrawer
import org.webrtc.RendererCommon.RendererEvents

/**
 * 映像の描画に使うコンテキストです。
 *
 * @param configAttributes EGL の設定。 [org.webrtc.EglBase] で定義されています。
 * @param drawer OpenGL の描画オブジェクト ([org.webrtc.RendererCommon.GlDrawer])
 *
 * @property rendererEvents 映像レンダラーのイベントハンドラ ([org.webrtc.RendererCommon.RendererEvents])
 */
class VideoRenderingContext(configAttributes: IntArray? = null,
                            val rendererEvents: RendererEvents? = null,
                            drawer: GlDrawer? = null) {

    companion object {
        private val TAG = VideoRenderingContext::class.simpleName!!

        /**
         * EGL ユーティリティ ([org.webrtc.EglBase])
         */
        var rootEglBase = EglBase.create()

        fun release() {
            rootEglBase.release()
        }
    }

   /**
     * EGL の設定。 [org.webrtc.EglBase] で定義されています。
     */
    val configAttributes: IntArray

    /**
     * OpenGL の描画オブジェクト ([org.webrtc.RendererCommon.GlDrawer]])
     */
    val drawer: GlDrawer

    internal var videoRendererPool = VideoRendererPool()

    init {
        SoraLogger.d(TAG, "initialize")

        if (configAttributes != null) {
            this.configAttributes = configAttributes
        } else {
            this.configAttributes = EglBase.CONFIG_PLAIN
        }

        if (drawer != null) {
            this.drawer = GlRectDrawer()
        } else {
            this.drawer = GlRectDrawer()
        }
    }

    internal fun initializeVideoRenderer(videoRenderer: VideoRenderer, releaseWhenDone: Boolean) {
        SoraLogger.d(TAG, "initialize video renderer => $videoRenderer")
        videoRenderer.initialize(this)
        if (releaseWhenDone) {
            videoRendererPool.add(videoRenderer)
        }
    }

    internal fun release() {
        SoraLogger.d(TAG, "release video renderers and EglBase")
        videoRendererPool.release()
    }

}