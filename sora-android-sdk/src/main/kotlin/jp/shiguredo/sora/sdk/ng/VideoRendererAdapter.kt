package jp.shiguredo.sora.sdk.ng

import org.webrtc.VideoFrame
import org.webrtc.VideoSink

internal class VideoRendererAdapter: VideoSink {

    var videoRenderer: VideoRenderer? = null

    override fun onFrame(frame: VideoFrame?) {
        videoRenderer?.onFrame(frame)
    }

}