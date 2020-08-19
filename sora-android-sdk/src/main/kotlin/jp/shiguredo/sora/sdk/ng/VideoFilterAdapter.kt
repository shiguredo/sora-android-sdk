package jp.shiguredo.sora.sdk.ng

import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink

internal class VideoFilterAdapter(val stream: MediaStream): VideoProcessor {

    private var sink: VideoSink? = null

    var filters: MutableList<VideoFilter> = mutableListOf()

    override fun onCapturerStarted(success: Boolean) {
        for (filter in filters) {
            filter.onCapturerStarted(success)
        }
    }

    override fun onCapturerStopped() {
        for (filter in filters) {
            filter.onCapturerStopped()
        }
    }

    override fun setSink(sink: VideoSink?) {
        this.sink = sink
    }

    override fun onFrameCaptured(frame: VideoFrame?) {
        var frame1 = frame
        for (filter in filters) {
            frame1 = filter.onFrame(frame1)
        }
        if (frame1 != null) {
            sink?.onFrame(frame1)
        }
    }

    fun addFilter(filter: VideoFilter) {
        filters.add(filter)
    }

    fun removeFilter(filter: VideoFilter) {
        filters.remove(filter)
    }

}