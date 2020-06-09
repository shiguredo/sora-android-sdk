package jp.shiguredo.sora.sdk.ng

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceHolder.Callback
import android.view.SurfaceView
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame

class VideoView @JvmOverloads constructor (context: Context,
                                           attrs: AttributeSet? = null,
                                           defStyleAttr: Int = 0) :
        SurfaceView(context, attrs, defStyleAttr), VideoRenderer, Callback {

    var nativeViewRenderer: SurfaceViewRenderer = SurfaceViewRenderer(context, attrs)

    var isMirrored: Boolean = false

    override fun shouldInitialization(): Boolean {
        return true
    }

    override fun shouldRelease(): Boolean {
        return true
    }

    override fun initialize(videoRenderingContext: VideoRenderingContext) {
        nativeViewRenderer.init(videoRenderingContext.eglBase.eglBaseContext,
                videoRenderingContext.rendererEvents,
                videoRenderingContext.configAttributes,
                videoRenderingContext.drawer)
    }

    override fun release() {
        nativeViewRenderer.release()
    }

    override fun onFrame(frame: VideoFrame?) {
        nativeViewRenderer.onFrame(frame)
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        nativeViewRenderer.surfaceCreated(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        nativeViewRenderer.surfaceChanged(holder, format, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        nativeViewRenderer.surfaceDestroyed(holder)
    }


}