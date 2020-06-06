package jp.shiguredo.sora.sdk.ng

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView
import org.webrtc.EglBase
import org.webrtc.GlRectDrawer
import org.webrtc.RendererCommon.GlDrawer
import org.webrtc.RendererCommon.RendererEvents
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame

class VideoView(context: Context,
                attrs: AttributeSet? = null,
                defStyleAttr: Int = 0) :
        SurfaceView(context, attrs, defStyleAttr), VideoRenderer {

    var nativeViewRenderer: SurfaceViewRenderer = SurfaceViewRenderer(context, attrs)

    var isMirrored: Boolean = false

    override fun init(renderingContext: RenderingContext) {
        nativeViewRenderer.init(renderingContext.eglBase.eglBaseContext,
                renderingContext.rendererEvents,
                renderingContext.configAttributes,
                renderingContext.drawer)
    }

    override fun release() {
        nativeViewRenderer.release()
    }

    override fun onFrame(frame: VideoFrame) {
        nativeViewRenderer.onFrame(frame)
    }

}