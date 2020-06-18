package jp.shiguredo.sora.sdk.ng

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.RendererCommon.RendererEvents
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

internal class VideoRendererAdapter(val stream: MediaStream): VideoSink, RendererEvents {

    var videoRenderer: VideoRenderer? = null

    override fun onFrame(frame: VideoFrame?) {
        SoraLogger.d("VideoRendererAdapter", "@onFrame => $frame")
        var frame = frame
        for (filter in stream.videoFilters) {
            frame = filter.onFrame(frame)
        }
        videoRenderer?.onFrame(frame)
    }

    override fun onFirstFrameRendered() {
        videoRenderer?.onFirstFrameRendered()
    }

    override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
        videoRenderer?.onFrameResolutionChanged(videoWidth, videoHeight, rotation)
    }

}