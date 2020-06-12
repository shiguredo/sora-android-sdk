package jp.shiguredo.sora.sdk.ng

import android.content.Context
import android.os.Looper
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceHolder.Callback
import android.view.SurfaceView
import jp.shiguredo.sora.sdk.Sora
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.RendererCommon.ScalingType
import org.webrtc.SurfaceViewRenderer
import org.webrtc.ThreadUtils
import org.webrtc.VideoFrame
import java.util.logging.Handler

class VideoView @JvmOverloads constructor (context: Context,
                                           attrs: AttributeSet? = null,
                                           defStyleAttr: Int = 0) :
        SurfaceView(context, attrs, defStyleAttr), VideoRenderer, Callback {

    companion object {
        internal val TAG = VideoView::class.simpleName!!
    }

    var nativeViewRenderer: SurfaceViewRenderer = SurfaceViewRenderer(context, attrs)

    override var isMirrored: Boolean = false
        set(value) {
            field = value
            nativeViewRenderer.setMirror(value)
        }

    override var hardwareScalerEnabled: Boolean = false
        set(value) {
            field = value
            nativeViewRenderer.setEnableHardwareScaler(value)
        }

    override var fpsReductionEnabled: Boolean = false
        set(value) {
            field = value
            if (!value) {
                nativeViewRenderer.disableFpsReduction()
            }
        }

    override var fpsReduction: Float = 0F
        set(value) {
            field = value
            nativeViewRenderer.setFpsReduction(value)
        }

    override fun shouldInitialization(): Boolean {
        return true
    }

    override fun shouldRelease(): Boolean {
        return true
    }

    override fun initialize(context: VideoRenderingContext) {
        SoraLogger.d(TAG, "initialize => $context")
        Sora.runOnUiThread {
            nativeViewRenderer.init(context.eglBase.eglBaseContext,
                    context.rendererEvents,
                    context.configAttributes,
                    context.drawer)
        }
    }

    override fun release() {
        nativeViewRenderer.release()
    }

    override fun setScalingType(scalingTypeMatchOrientation: ScalingType?,
                                scalingTypeMismatchOrientation: ScalingType?) {
        nativeViewRenderer.setScalingType(scalingTypeMatchOrientation,
                scalingTypeMismatchOrientation)
    }

    override fun resume() {
        if (fpsReductionEnabled) {
            if (fpsReduction != null)
                nativeViewRenderer.setFpsReduction(fpsReduction!!)
            else
                nativeViewRenderer.disableFpsReduction()
        } else
            nativeViewRenderer.disableFpsReduction()
    }

    override fun pause() {
        nativeViewRenderer.pauseVideo()
    }

    override fun clear() {
        nativeViewRenderer.clearImage()
    }

    override fun onFrame(frame: VideoFrame?) {
        SoraLogger.d(TAG, "onFrame => $frame")
        nativeViewRenderer.onFrame(frame)
    }

    override fun onFirstFrameRendered() {
        nativeViewRenderer.onFirstFrameRendered()
    }

    override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
        nativeViewRenderer.onFrameResolutionChanged(videoWidth, videoHeight, rotation)
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
