package jp.shiguredo.sora.sdk.ng

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import jp.shiguredo.sora.sdk.R
import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.RendererCommon.ScalingType
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoTrack

class VideoView @JvmOverloads constructor (context: Context,
                                           attrs: AttributeSet? = null,
                                           defStyleAttr: Int = 0) :
        LinearLayout(context, attrs, defStyleAttr), VideoRenderer {

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

    init {
        //holder.addCallback(this)

        val layout = LayoutInflater.from(context).inflate(R.layout.videoview, this)
        nativeViewRenderer = layout.findViewById(R.id.renderer)
    }

    override fun attachToVideoTrack(track: VideoTrack) {
        SoraLogger.d(TAG, "attach $nativeViewRenderer to video track $track")
        track.setEnabled(true)
        track.addSink(nativeViewRenderer)
    }

    override fun detachFromVideoTrack(track: VideoTrack) {
        SoraLogger.d(TAG, "detach $nativeViewRenderer from video track $track")
        track.setEnabled(false)
        track.removeSink(nativeViewRenderer)
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
        nativeViewRenderer.onFrame(frame)
    }

    override fun onFirstFrameRendered() {
        nativeViewRenderer.onFirstFrameRendered()
    }

    override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
        nativeViewRenderer.onFrameResolutionChanged(videoWidth, videoHeight, rotation)
    }

}
