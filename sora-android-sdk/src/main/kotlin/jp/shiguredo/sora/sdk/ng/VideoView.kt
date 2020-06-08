package jp.shiguredo.sora.sdk.ng

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame

class VideoView(context: Context,
                attrs: AttributeSet? = null,
                defStyleAttr: Int = 0) :
        SurfaceView(context, attrs, defStyleAttr), VideoRenderer {

    var nativeViewRenderer: SurfaceViewRenderer = SurfaceViewRenderer(context, attrs)

    var isMirrored: Boolean = false

    override fun init(videoRenderingContext: VideoRenderingContext) {
        nativeViewRenderer.init(videoRenderingContext.eglBase.eglBaseContext,
                videoRenderingContext.rendererEvents,
                videoRenderingContext.configAttributes,
                videoRenderingContext.drawer)
    }

    override fun release() {
        nativeViewRenderer.release()
    }

    override fun onFrame(frame: VideoFrame) {
        nativeViewRenderer.onFrame(frame)
    }

}