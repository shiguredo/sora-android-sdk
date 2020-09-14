package jp.shiguredo.sora.sdk2

import jp.shiguredo.sora.sdk.util.SoraLogger
import org.webrtc.RendererCommon.RendererEvents
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

internal class VideoRendererAdapter(val stream: MediaStream): VideoSink, RendererEvents {

    var videoRenderer: VideoRenderer? = null

    override fun onFrame(frame: VideoFrame?) {
        SoraLogger.d("VideoRendererAdapter", "@onFrame => $frame")
        var frame1 = frame
        for (filter in stream.videoFilters) {
            frame1 = filter.onFrame(frame1)
        }
        videoRenderer?.onFrame(frame1)
    }

    override fun onFirstFrameRendered() {
        videoRenderer?.onFirstFrameRendered()
    }

    override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
        videoRenderer?.onFrameResolutionChanged(videoWidth, videoHeight, rotation)
    }

}