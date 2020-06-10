package jp.shiguredo.sora.sdk.ng

import org.webrtc.RendererCommon.RendererEvents
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

internal class VideoRendererAdapter: VideoSink, RendererEvents {

    var videoRenderer: VideoRenderer? = null

    override fun onFrame(frame: VideoFrame?) {
        videoRenderer?.onFrame(frame)
    }

    override fun onFirstFrameRendered() {
        videoRenderer?.onFirstFrameRendered()
    }

    override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
        videoRenderer?.onFrameResolutionChanged(videoWidth, videoHeight, rotation)
    }

}