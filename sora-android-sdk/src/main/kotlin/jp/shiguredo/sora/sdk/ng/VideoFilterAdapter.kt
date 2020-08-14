package jp.shiguredo.sora.sdk.ng

import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink

internal class VideoFilterAdapter(stream: MediaStream): VideoProcessor {

    private var sink: VideoSink? = null

    var filters: MutableList<VideoFilter> = mutableListOf()

    override fun onCapturerStopped() {
        TODO("Not yet implemented")
    }

    override fun onCapturerStarted(p0: Boolean) {
        TODO("Not yet implemented")
    }

    override fun setSink(sink: VideoSink?) {
        this.sink = sink
    }

    override fun onFrameCaptured(frame: VideoFrame?) {
        var frame = frame
        for (filter in filters) {
            frame = filter.onFrame(frame)
        }
        if (frame != null) {
            sink?.onFrame(frame!!)
        }
    }

    fun addFilter(filter: VideoFilter) {
        filters.add(filter)
    }

    fun removeFilter(filter: VideoFilter) {
        // TODO
    }

}