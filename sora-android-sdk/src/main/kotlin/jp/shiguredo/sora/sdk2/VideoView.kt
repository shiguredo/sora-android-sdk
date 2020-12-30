package jp.shiguredo.sora.sdk2

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

    override var state: VideoRenderer.State = VideoRenderer.State.NOT_INITIALIZED

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
     * [VideoRenderer.shouldInitialize] を参照
     */
    override val shouldInitialize: Boolean = true

    /**
     * [VideoRenderer.shouldRelease] を参照
     */
    override val shouldRelease: Boolean = true

    /**
     * [VideoRenderer.initialize] を参照
     */
    override fun initialize(videoRenderingContext: VideoRenderingContext) {
        SoraLogger.d(TAG, "initialize => $videoRenderingContext")

        when (state) {
            VideoRenderer.State.RUNNING -> {
                SoraLogger.d(TAG, "already initialized")
            }
            else -> {
                Sora.runOnUiThread {
                    nativeViewRenderer.init(VideoRenderingContext.rootEglBase.eglBaseContext,
                            videoRenderingContext.rendererEvents,
                            videoRenderingContext.configAttributes,
                            videoRenderingContext.drawer)
                }
            }
        }
    }

    /**
     * [VideoRenderer.release] を参照
     */
    override fun release() {
        SoraLogger.d(TAG, "release")

        when (state) {
            VideoRenderer.State.NOT_INITIALIZED -> {
                SoraLogger.d(TAG, "not initialized")
            }
            VideoRenderer.State.RELEASED -> {
                SoraLogger.d(TAG, "already released")
            }
            VideoRenderer.State.RUNNING -> {
                Sora.runOnUiThread {
                    nativeViewRenderer.release()
                }
            }
        }
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
