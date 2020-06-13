package jp.shiguredo.sora.sdk.ng

import org.webrtc.RendererCommon
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack

interface VideoRenderer {

    var isMirrored: Boolean

    var hardwareScalerEnabled: Boolean

    var fpsReduction: Float

    var fpsReductionEnabled: Boolean

    fun attachToVideoTrack(track: VideoTrack)

    fun detachFromVideoTrack(track: VideoTrack)

    fun shouldInitialization(): Boolean

    fun shouldRelease(): Boolean

    fun initialize(videoRenderingContext: VideoRenderingContext)

    fun release()

    fun setScalingType(scalingTypeMatchOrientation: RendererCommon.ScalingType?,
                       scalingTypeMismatchOrientation: RendererCommon.ScalingType?)

    fun resume()

    fun pause()

    fun clear()

    fun onFrame(frame: VideoFrame?)

    fun onFirstFrameRendered()

    fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int)

}