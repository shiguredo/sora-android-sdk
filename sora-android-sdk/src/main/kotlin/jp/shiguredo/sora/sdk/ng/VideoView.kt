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

/**
 * 映像を描画する UI コンポーネントです。
 *
 * @constructor
 * オブジェクトを生成します。
 *
 * @param context コンテキスト
 * @param attrs 属性のセット
 * @param defStyleAttr 基本スタイル
 *
 * @see [android.widget.LinearLayout]
 */
class VideoView @JvmOverloads constructor (context: Context,
                                           attrs: AttributeSet? = null,
                                           defStyleAttr: Int = 0) :
        LinearLayout(context, attrs, defStyleAttr), VideoRenderer {

    internal companion object {
        internal val TAG = VideoView::class.simpleName!!
    }

    /**
     * ネイティブの映像レンダラー ([org.webrtc.SurfaceViewRenderer])
     */
    var nativeViewRenderer: SurfaceViewRenderer = SurfaceViewRenderer(context, attrs)

    /**
     * [VideoRenderer.isMirrored] を参照
     */
    override var isMirrored: Boolean = false
        set(value) {
            field = value
            nativeViewRenderer.setMirror(value)
        }

    /**
     * [VideoRenderer.hardwareScalerEnabled] を参照
     */
    override var hardwareScalerEnabled: Boolean = false
        set(value) {
            field = value
            nativeViewRenderer.setEnableHardwareScaler(value)
        }

    /**
     * [VideoRenderer.fpsReductionEnabled] を参照
     */
    override var fpsReductionEnabled: Boolean = false
        set(value) {
            field = value
            if (!value) {
                nativeViewRenderer.disableFpsReduction()
            }
        }

    /**
     * [VideoRenderer.fpsReduction] を参照
     */
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

    /**
     * [VideoRenderer.attachToVideoTrack] を参照
     */
    override fun attachToVideoTrack(track: VideoTrack) {
        SoraLogger.d(TAG, "attach $nativeViewRenderer to video track $track")
        track.setEnabled(true)
        track.addSink(nativeViewRenderer)
    }

    /**
     * [VideoRenderer.detachFromVideoTrack] を参照
     */
    override fun detachFromVideoTrack(track: VideoTrack) {
        SoraLogger.d(TAG, "detach $nativeViewRenderer from video track $track")
        track.setEnabled(false)
        track.removeSink(nativeViewRenderer)
    }

    /**
     * [VideoRenderer.shouldInitialization] を参照
     */
    override fun shouldInitialization(): Boolean {
        return true
    }

    /**
     * [VideoRenderer.shouldRelease] を参照
     */
    override fun shouldRelease(): Boolean {
        return true
    }

    /**
     * [VideoRenderer.initialize] を参照
     */
    override fun initialize(videoRenderingContext: VideoRenderingContext) {
        SoraLogger.d(TAG, "initialize => $videoRenderingContext")
        Sora.runOnUiThread {
            nativeViewRenderer.init(videoRenderingContext.eglBase.eglBaseContext,
                    videoRenderingContext.rendererEvents,
                    videoRenderingContext.configAttributes,
                    videoRenderingContext.drawer)
        }
    }

    /**
     * [VideoRenderer.release] を参照
     */
    override fun release() {
        nativeViewRenderer.release()
    }

    /**
     * [VideoRenderer.setScalingType] を参照
     */
    override fun setScalingType(scalingTypeMatchOrientation: ScalingType?,
                                scalingTypeMismatchOrientation: ScalingType?) {
        nativeViewRenderer.setScalingType(scalingTypeMatchOrientation,
                scalingTypeMismatchOrientation)
    }

    /**
     * [VideoRenderer.resume] を参照
     */
    override fun resume() {
        if (fpsReductionEnabled) {
            nativeViewRenderer.setFpsReduction(fpsReduction)
        } else
            nativeViewRenderer.disableFpsReduction()
    }

    /**
     * [VideoRenderer.pause] を参照
     */
    override fun pause() {
        nativeViewRenderer.pauseVideo()
    }

    /**
     * [VideoRenderer.clear] を参照
     */
    override fun clear() {
        nativeViewRenderer.clearImage()
    }

    /**
     * [VideoRenderer.onFrame] を参照
     */
    override fun onFrame(frame: VideoFrame?) {
        nativeViewRenderer.onFrame(frame)
    }

    /**
     * [VideoRenderer.onFirstFrameRendered] を参照
     */
    override fun onFirstFrameRendered() {
        nativeViewRenderer.onFirstFrameRendered()
    }

    /**
     * [VideoRenderer.onFrameResolutionChanged] を参照
     */
    override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
        nativeViewRenderer.onFrameResolutionChanged(videoWidth, videoHeight, rotation)
    }

}
